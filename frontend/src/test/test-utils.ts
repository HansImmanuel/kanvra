import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// Ensure RTL unmounts between tests in the jsdom environment.
afterEach(() => {
  cleanup();
});