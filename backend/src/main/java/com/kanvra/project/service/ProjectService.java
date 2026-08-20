package com.kanvra.project.service;

import com.kanvra.auth.model.User;
import com.kanvra.auth.repository.UserRepository;
import com.kanvra.board.model.Board;
import com.kanvra.board.model.BoardColumn;
import com.kanvra.board.repository.BoardColumnRepository;
import com.kanvra.board.repository.BoardRepository;
import com.kanvra.common.error.ForbiddenOperationException;
import com.kanvra.common.error.ResourceNotFoundException;
import com.kanvra.common.error.ValidationException;
import com.kanvra.common.error.ValidationFieldError;
import com.kanvra.kafka.event.DomainEvent;
import com.kanvra.kafka.event.KafkaEventTypes;
import com.kanvra.outbox.EventPublisher;
import com.kanvra.project.dto.AddMemberRequest;
import com.kanvra.project.dto.MemberResponse;
import com.kanvra.project.dto.ProjectRequest;
import com.kanvra.project.dto.ProjectResponse;
import com.kanvra.project.model.Project;
import com.kanvra.project.model.ProjectMember;
import com.kanvra.project.model.ProjectMemberId;
import com.kanvra.project.repository.ProjectMemberRepository;
import com.kanvra.project.repository.ProjectRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project management and membership (docs/SPEC.md §4). Project creation also
 * provisions the default board with TODO / IN PROGRESS / DONE columns and the
 * OWNER membership row.
 */
@Service
public class ProjectService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    private static final List<String> DEFAULT_COLUMNS = List.of("TODO", "IN PROGRESS", "DONE");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final BoardRepository boardRepository;
    private final BoardColumnRepository columnRepository;
    private final UserRepository userRepository;
    private final ProjectAccessService access;
    private final EventPublisher eventPublisher;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                          BoardRepository boardRepository, BoardColumnRepository columnRepository,
                          UserRepository userRepository, ProjectAccessService access,
                          EventPublisher eventPublisher) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.boardRepository = boardRepository;
        this.columnRepository = columnRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ProjectResponse create(Long userId, ProjectRequest request) {
        Project project = new Project();
        project.setName(request.name().trim());
        project.setDescription(request.description());
        project.setOwnerId(userId);
        project.setStatus(STATUS_ACTIVE);
        projectRepository.save(project);

        ProjectMember owner = new ProjectMember();
        owner.setId(new ProjectMemberId(project.getId(), userId));
        owner.setRole(ProjectAccessService.ROLE_OWNER);
        memberRepository.save(owner);

        createDefaultBoard(project);

        eventPublisher.publish(DomainEvent.of(
                KafkaEventTypes.PROJECT_CREATED, userId, project.getId(),
                KafkaEventTypes.AGGREGATE_PROJECT, project.getId(),
                Map.of("projectId", project.getId(), "name", project.getName(),
                        "actorName", actorName(userId))));

        return ProjectResponse.from(project);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> list(Long userId, Pageable pageable) {
        return projectRepository.findAccessible(userId, pageable).map(ProjectResponse::from);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long userId, Long projectId) {
        access.requireMembership(userId, projectId);
        return ProjectResponse.from(access.requireProject(projectId));
    }

    @Transactional
    public ProjectResponse update(Long userId, Long projectId, ProjectRequest request) {
        Project project = access.requireProject(projectId);
        access.requireOwner(userId, projectId);
        project.setName(request.name().trim());
        project.setDescription(request.description());
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse archive(Long userId, Long projectId) {
        Project project = access.requireProject(projectId);
        access.requireOwner(userId, projectId);
        if (STATUS_ARCHIVED.equals(project.getStatus())) {
            return ProjectResponse.from(project);
        }
        project.setStatus(STATUS_ARCHIVED);

        eventPublisher.publish(DomainEvent.of(
                KafkaEventTypes.PROJECT_ARCHIVED, userId, projectId,
                KafkaEventTypes.AGGREGATE_PROJECT, projectId,
                Map.of("projectId", projectId, "name", project.getName(),
                        "actorName", actorName(userId))));

        return ProjectResponse.from(project);
    }

    @Transactional
    public MemberResponse addMember(Long ownerId, Long projectId, AddMemberRequest request) {
        access.requireProject(projectId);
        access.requireOwner(ownerId, projectId);

        User target = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        if (memberRepository.findByIdProjectIdAndIdUserId(projectId, target.getId()).isPresent()) {
            throw new ValidationException(List.of(
                    new ValidationFieldError("userId", "User is already a member of this project")));
        }

        ProjectMember member = new ProjectMember();
        member.setId(new ProjectMemberId(projectId, target.getId()));
        member.setRole(request.role());
        memberRepository.save(member);

        eventPublisher.publish(DomainEvent.of(
                KafkaEventTypes.PROJECT_MEMBER_ADDED, ownerId, projectId,
                KafkaEventTypes.AGGREGATE_PROJECT, projectId,
                Map.of("projectId", projectId, "userId", target.getId(), "role", request.role(),
                        "actorName", actorName(ownerId))));

        return MemberResponse.from(member, target.getName());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long userId, Long projectId) {
        access.requireMembership(userId, projectId);
        List<ProjectMember> members = memberRepository.findByIdProjectId(projectId);
        Set<Long> userIds = members.stream().map(m -> m.getId().getUserId()).collect(Collectors.toSet());
        Map<Long, String> names = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        return members.stream()
                .map(m -> MemberResponse.from(m, names.getOrDefault(m.getId().getUserId(), "Unknown user")))
                .toList();
    }

    @Transactional
    public void removeMember(Long ownerId, Long projectId, Long userId) {
        Project project = access.requireProject(projectId);
        access.requireOwner(ownerId, projectId);
        if (project.getOwnerId().equals(userId)) {
            throw new ForbiddenOperationException("The project owner cannot be removed");
        }
        ProjectMemberId id = new ProjectMemberId(projectId, userId);
        if (!memberRepository.existsById(id)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User is not a member of this project");
        }
        memberRepository.deleteById(id);
    }

    private void createDefaultBoard(Project project) {
        Board board = new Board();
        board.setProjectId(project.getId());
        board.setName(project.getName());
        board.setStatus(STATUS_ACTIVE);
        boardRepository.save(board);

        for (int i = 0; i < DEFAULT_COLUMNS.size(); i++) {
            BoardColumn column = new BoardColumn();
            column.setBoardId(board.getId());
            column.setName(DEFAULT_COLUMNS.get(i));
            column.setPosition(i);
            columnRepository.save(column);
        }
    }

    private String actorName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse("Someone");
    }
}
