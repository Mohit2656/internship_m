"use client";

import React from "react";

export interface TabItem<T extends string | number> {
  id: string;
  value: T;
  label: string;
  badge?: string | number | null;
  badgeClass?: string;
  count?: number;
}

interface SegmentedTabsProps<T extends string | number> {
  tabs: TabItem<T>[];
  activeTab: T;
  onChange: (value: T) => void;
  ariaLabel?: string;
  className?: string;
  compact?: boolean;
}

/**
 * SegmentedTabs
 * A clean, compact institutional segmented control matching the IMS theme tokens.
 * Selected: Subtle light-blue background (#eff6ff), dark blue text (#1d4ed8), subtle border.
 * Inactive: Neutral background, slate text, subtle hover.
 */
export function SegmentedTabs<T extends string | number>({
  tabs,
  activeTab,
  onChange,
  ariaLabel = "Navigation tabs",
  className = "",
  compact = false,
}: SegmentedTabsProps<T>) {
  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className={`inline-flex items-center gap-1 rounded-lg bg-slate-100/90 p-1 border border-slate-200/80 shadow-xs ${className}`}
    >
      {tabs.map((tab) => {
        const isSelected = tab.value === activeTab;
        return (
          <button
            key={String(tab.value)}
            type="button"
            role="tab"
            id={tab.id}
            aria-selected={isSelected}
            aria-controls={`panel-${tab.id.replace(/^tab-/, "")}`}
            onClick={() => onChange(tab.value)}
            className={`segmented-tab inline-flex items-center gap-2 rounded-md font-medium transition-all focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-1 ${
              compact ? "px-3 py-1 text-xs" : "px-3.5 py-1.5 text-xs sm:text-sm"
            } ${
              isSelected
                ? "bg-blue-50 text-blue-700 font-semibold border border-blue-200/90 shadow-xs"
                : "bg-transparent text-slate-600 hover:text-slate-900 hover:bg-slate-200/50 border border-transparent"
            }`}
          >
            <span>{tab.label}</span>
            {tab.badge && (
              <span
                className={`rounded-full px-2 py-0.5 text-[10px] font-semibold border transition-colors ${
                  isSelected
                    ? "border-blue-200 bg-blue-100 text-blue-800"
                    : tab.badgeClass || "border-slate-200 bg-slate-200/70 text-slate-700"
                }`}
              >
                {tab.badge}
              </span>
            )}
            {typeof tab.count === "number" && (
              <span
                className={`rounded-full px-1.5 py-0.5 text-[10px] font-bold ${
                  isSelected ? "bg-blue-100 text-blue-800" : "bg-slate-200 text-slate-600"
                }`}
              >
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
