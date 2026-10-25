import type { Metadata } from "next";
import "./globals.css";
import { AuthProvider } from "@/lib/auth";
import { WelcomeScreen } from "@/components/WelcomeScreen";
import { THEME_INITIALIZER } from "@/lib/theme";

export const metadata: Metadata = {
  title: "Internship Management System",
  description: "Institutional internship management",
  icons: { icon: { url: "/branding/college-logo.png", type: "image/png", sizes: "150x150" } }
};
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en" suppressHydrationWarning><head><script id="ims-theme" dangerouslySetInnerHTML={{ __html: THEME_INITIALIZER }} /></head><body><AuthProvider><WelcomeScreen />{children}</AuthProvider></body></html>;
}

