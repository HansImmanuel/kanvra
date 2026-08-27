// design-tokens.ts — Kanvra visual redesign tokens.
//
// Single source of truth for the redesign. Mirrored by the @theme block in
// app/globals.css (Tailwind v4): colors / radius / shadows there must stay in
// sync with the values here. Values map to Tailwind's default slate palette so
// existing utilities keep rendering identically during migration.

export const colors = {
  // --- Surfaces ---
  background: "#f1f5f9",       // app canvas (slate-100)
  surface: "#ffffff",          // cards, panels, modals
  elevated: "#f8fafc",         // sidebar, inset rows (slate-50)
  border: "#e2e8f0",           // dividers, sidebar rail (slate-200)
  borderStrong: "#cbd5e1",     // input/card outlines (slate-300)

  // --- Brand / actions ---
  primary: "#334155",          // action buttons today (slate-700) — swap for a brand hue when decided
  primaryHover: "#475569",     // hover state (slate-600)
  secondary: "#6366f1",        // accent (indigo-500) — unread indicators
  secondaryBg: "#eef2ff",      // indigo-50 — unread notification rows
  chartBar: "#3b82f6",         // analytics bars (blue-500) — merge into secondary on redesign

  // --- Text ---
  text: "#1e293b",             // headings (slate-800)
  textBody: "#334155",         // strong body (slate-700)
  textSecondary: "#475569",    // secondary copy (slate-600)
  muted: "#64748b",            // muted copy, loading states (slate-500)
  faint: "#94a3b8",            // metadata, timestamps (slate-400)

  // --- Feedback ---
  success: "#16a34a",
  successBg: "#dcfce7",        // green-100 notices
  successBorder: "#86efac",
  warning: "#d97706",
  warningBg: "#fef3c7",
  error: "#dc2626",
  errorBg: "#fee2e2",          // red-100 error banners
  errorBorder: "#fca5a5",
  info: "#0ea5e9",

  // --- Overlay ---
  backdrop: "rgba(15, 23, 42, 0.4)", // modal backdrop (slate-900/40)

  // --- Task priority / status indicators (fg = text, bg = chip fill) ---
  status: {
    critical: { fg: "#881337", bg: "#fda4af" }, // rose-900 / rose-300
    high: { fg: "#991b1b", bg: "#fecaca" },     // red-800 / red-200
    medium: { fg: "#92400e", bg: "#fde68a" },   // amber-800 / amber-200
    low: { fg: "#166534", bg: "#bbf7d0" },      // green-800 / green-200
    none: { fg: "#1e293b", bg: "#e2e8f0" }      // unlabelled priority fallback
  }
} as const;

/** Keys of {@link colors.status}, usable as a Badge tone. */
export type StatusTone = keyof typeof colors["status"];

export const spacing = {
  // --- Chrome ---
  sidebarWidth: "12rem",       // w-48
  sidebarItemPadX: "0.75rem",
  sidebarItemPadY: "0.5rem",
  headerPadX: "1.5rem",
  headerPadY: "1rem",
  pageGutter: "1.5rem",        // <main> p-6

  // --- Cards & panels ---
  cardPadSm: "0.75rem",        // task card p-3
  cardPad: "1rem",             // panel p-4
  cardPadLg: "1.25rem",        // overview/settings sections p-5

  // --- Board columns ---
  columnWidth: "18rem",        // w-72
  addColumnRailWidth: "14rem", // w-56 "+ Add column" rail
  columnGap: "1rem",

  // --- Modals & forms ---
  modalPad: "1.25rem",
  modalWidthSm: "32rem",       // max-w-lg — task detail
  modalWidthMd: "42rem",       // max-w-2xl — activity, settings
  modalWidthLg: "48rem",       // max-w-3xl — dashboard/analytics content width
  fieldGap: "0.75rem",         // form grid gap-3
  controlPadY: "0.375rem"      // input py-1.5
} as const;

export const typography = {
  fontFamily:
    'ui-sans-serif, system-ui, sans-serif, "Apple Color Emoji", "Segoe UI Emoji"',
  nav: { size: "0.875rem", weight: 500, lineHeight: "1.25rem" },      // text-sm medium
  projectName: { size: "1.25rem", weight: 700, lineHeight: "1.75rem" }, // workspace header h1
  pageTitle: { size: "1.5rem", weight: 700, lineHeight: "2rem" },     // login/global dashboard h1
  sectionTitle: { size: "1.125rem", weight: 600, lineHeight: "1.75rem" },
  taskTitle: { size: "0.875rem", weight: 500, lineHeight: "1.25rem" },
  body: { size: "0.875rem", weight: 400, lineHeight: "1.25rem" },
  metadata: { size: "0.75rem", weight: 400, lineHeight: "1rem" },     // version chips
  label: { size: "0.875rem", weight: 500, lineHeight: "1.25rem" },
  button: { size: "0.875rem", weight: 500, lineHeight: "1.25rem" },   // target weight on redesign
  timestamp: { size: "0.75rem", weight: 400, lineHeight: "1rem" }
} as const;

export const radius = {
  sm: "0.25rem",    // rounded — inputs, small buttons, chips
  md: "0.5rem",     // rounded-lg — cards, panels, dropdowns
  lg: "0.75rem",    // rounded-xl — modal dialog
  full: "9999px"    // pills, avatars, badge dots
} as const;

export const shadows = {
  card: "0 1px 2px 0 rgb(0 0 0 / 0.05)",                              // shadow-sm
  toast: "0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)", // shadow-lg
  overlay:
    "0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)" // shadow-xl
} as const;
