export type StudentNotification = { id: number; type: string; title: string; message: string; rejectionReason: string | null; mentorName: string | null; actionUrl: string; createdAt: string; readAt: string | null };
export type NotificationInbox = { content: StudentNotification[]; page: number; totalElements: number; totalPages: number; unreadCount: number };
export const NOTIFICATIONS_CHANGED = "ims:notifications-changed";
export function syncNotificationCount() { window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED)); }
