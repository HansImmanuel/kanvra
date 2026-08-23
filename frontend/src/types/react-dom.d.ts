// Minimal declarations for the `react-dom` shim package (React 19 devDepot).
// The package re-exports `createPortal` but ships no type declarations, so the
// Next.js tsconfig (`include: ["**/*.ts"]`) picks up this module shim.
declare module "react-dom" {
  import type { ReactNode } from "react";
  export function createPortal(children: ReactNode, container: Element | null): ReactNode;
}