import axios from "axios";

export const api = axios.create({
  baseURL: "/api/v1",
  withCredentials: true,
  timeout: 10_000,
  headers: { "Content-Type": "application/json" },
});

export type Role = "ADMIN" | "STUDENT" | "MENTOR";
export type AuthUser = { id: number; email: string; role: Role; roleLabel: string; mustChangePassword: boolean };
export type ApiError = { code?: string; message?: string; fieldErrors?: Record<string, string> };
