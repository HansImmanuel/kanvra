import js from "@eslint/js";
import tseslint from "typescript-eslint";

// Flat config for the Sprint-4 lint gate (docs/TECH_DOC.md §22). Kept lean:
// JS recommended + typescript-eslint recommended, no stylistic rules.
export default tseslint.config(
  {
    ignores: [".next/**", "node_modules/**", "next-env.d.ts", "*.config.mjs"]
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    rules: {
      "@typescript-eslint/no-unused-vars": [
        "error",
        { argsIgnorePattern: "^_", varsIgnorePattern: "^_", caughtErrors: "none" }
      ],
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-non-null-assertion": "off"
    }
  }
);