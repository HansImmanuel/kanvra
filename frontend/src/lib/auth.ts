// Auth helpers (docs/TECH_DOC.md §14 lib/auth).
import { get, post } from "./api";
import type { User } from "../types";

export const currentUser = (): Promise<User> => get<User>("/api/v1/me");

/** Same as currentUser but null on failure — used by pages to infer guest vs logged-in. */
export function currentUserSafe(): Promise<User | null> {
  return get<User>("/api/v1/me").then(
    (u) => u,
    () => null
  );
}

export const login = (email: string, password: string): Promise<User> =>
  post<User>("/api/v1/auth/login", { email, password }, false);

export const register = (name: string, email: string, password: string): Promise<User> =>
  post<User>("/api/v1/auth/register", { name, email, password }, false);

export const logout = async (): Promise<void> => {
  await post<void>("/api/v1/auth/logout", undefined, false).catch(() => {});
};