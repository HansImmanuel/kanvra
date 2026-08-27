"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { login, register } from "@/lib/auth";
import { Button, Input } from "@/components/ui";

type Mode = "login" | "register";

export default function LoginPage() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      if (mode === "login") {
        await login(email, password);
      } else {
        await register(name, email, password);
      }
      router.push("/dashboard");
      router.refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Authentication failed");
    }
  };

  return (
    <main className="mx-auto max-w-md p-8">
      <h1 className="text-2xl font-bold text-slate-800 mb-4">Kanvra</h1>
      <div className="rounded-lg border border-slate-300 bg-white p-6 shadow">
        <div className="flex gap-2 mb-4">
          <button
            className={"px-4 py-2 rounded " + (mode === "login" ? "bg-slate-700 text-white" : "bg-slate-200 text-slate-600")}
            onClick={() => setMode("login")}
          >
            Log in
          </button>
          <button
            className={"px-4 py-2 rounded " + (mode === "register" ? "bg-slate-700 text-white" : "bg-slate-200 text-slate-600")}
            onClick={() => setMode("register")}
          >
            Register
          </button>
        </div>
        <form onSubmit={submit} className="flex flex-col gap-2">
          {mode === "register" && (
            <Input
              fieldSize="lg"
              className="w-full"
              placeholder="Name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          )}
          <Input
            fieldSize="lg"
            className="w-full"
            type="email"
            required
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <Input
            fieldSize="lg"
            className="w-full"
            type="password"
            required
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <p className="text-sm text-red-600">{error}</p>}
          <Button variant="primary" size="md" type="submit">
            {mode === "login" ? "Log in" : "Create account"}
          </Button>
        </form>
      </div>
    </main>
  );
}