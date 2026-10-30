export type NotificationRole = "STUDENT" | "MENTOR" | "ADMIN";
export type AppNotification = { id: number; type: string; title: string; message: string; rejectionReason: string | null; mentorName: string | null; actionUrl: string; createdAt: string; readAt: string | null };
export type StudentNotification = AppNotification;
export type NotificationInbox = { content: AppNotification[]; page: number; totalElements: number; totalPages: number; unreadCount: number };
export const NOTIFICATIONS_CHANGED = "ims:notifications-changed";
export function syncNotificationCount() { window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED)); }
