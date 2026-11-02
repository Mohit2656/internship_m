"use client";

import { useEffect, useRef, type ReactNode } from "react";

/** Native modal focus containment and Escape behavior for shared overlays. */
export function ModalDialog({ children, label, onClose, busy = false, className = "" }: {
  children: ReactNode;
  label: string;
  onClose: () => void;
  busy?: boolean;
  className?: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLElement | null>(null);
  useEffect(() => {
    trigger.current ??= document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    const element = dialog.current;
    document.body.style.overflow = "hidden";
    element?.showModal();
    return () => {
      if (element?.open) element.close();
      document.body.style.overflow = previousOverflow;
      if (trigger.current?.isConnected) trigger.current.focus();
    };
  }, []);

  return <dialog ref={dialog} aria-label={label} aria-busy={busy || undefined}
    className={`ui-dialog m-auto max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-3xl overflow-y-auto rounded-2xl border border-slate-200 bg-white p-0 shadow-2xl backdrop:bg-slate-950/60 ${className}`}
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}
    onClick={event => {
      if (busy || event.target !== event.currentTarget) return;
      const bounds = event.currentTarget.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
    }}>
    {children}
  </dialog>;
}
