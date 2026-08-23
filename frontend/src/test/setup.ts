import "@testing-library/jest-dom/vitest";

// jsdom does not implement crypto.randomUUID in all versions; polyfill so the
// api client's idempotency-key generation is deterministic-safe in tests.
import { webcrypto } from "node:crypto";
if (typeof globalThis.crypto === "undefined") {
  Object.defineProperty(globalThis, "crypto", { value: webcrypto });
}