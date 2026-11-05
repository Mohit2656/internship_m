"use client";

import { useEffect, useState, useMemo, useCallback } from "react";
import axios from "axios";
import { api } from "@/lib/api";
import { useAcademicSessions } from "@/lib/academic-sessions";
import { SavedReportDto } from "./SavedReportsList";

export type FieldKind = "TEXT" | "NUMBER" | "DATE" | "DATETIME" | "BOOLEAN" | "CATEGORICAL";

export type CatalogChoice = {
  value: string;
  label: string;
};

export type CatalogField = {
  id: string;
  label: string;
  category: string;
  kind: FieldKind;
  choices: CatalogChoice[] | null;
  dynamicCompany: boolean;
  aggregatable: boolean;
  allowedOperators: string[];
};

export type FilterRule = {
  field: string;
  operator: string;
  value?: string;
  secondaryValue?: string;
  values?: string[];
};

export type FilterGroup = {
  conjunction: "AND" | "OR";
  rules: FilterRule[];
  groups?: FilterGroup[];
};

export type CustomReportRequest = {
  title: string;
  scope: "INTERNSHIP" | "STUDENT";
  academicSessionId?: number | null;
  columns: string[];
  filterGroup?: FilterGroup | null;
  groupBy?: string;
  sortBy?: string;
  sortDirection?: "ASC" | "DESC";
  format?: "PREVIEW" | "PDF" | "EXCEL" | "CSV";
  page?: number;
  size?: number;
  search?: string;
};

export type GroupData = {
  groupValue: string;
  recordCount: number;
  summary: Record<string, unknown>;
  rows: Record<string, unknown>[];
};

export type CustomReportResponse = {
  institutionName: string;
  departmentName: string;
  headerTitle: string;
  reportTitle: string;
  academicSessionName: string;
  generatedAt: string;
  timezone: string;
  filterSummary: string;
  columns: CatalogField[];
  scope: string;
  groupBy: string;
  totalStudents: number;
  totalInternships: number;
  totalRecords: number;
  overallSummary: Record<string, unknown>;
  groups: GroupData[];
  page: number;
  size: number;
  totalPages: number;
};

interface CustomReportBuilderProps {
  initialReport?: SavedReportDto | null;
  onSaved?: () => void;
}

export function parseFilenameFromContentDisposition(disposition?: string): string | null {
  if (!disposition) return null;

  // 1. Check RFC-5987 / RFC-6266 format first: filename*=UTF-8''... (or ISO-8859-1)
  const starMatch = disposition.match(/filename\*\s*=\s*(?:[A-Za-z0-9_-]+)?''([^;\r\n]+)/i);
  if (starMatch && starMatch[1]) {
    try {
      const decoded = decodeURIComponent(starMatch[1].trim().replace(/^["']|["']$/g, ""));
      if (decoded) return decoded;
    } catch {
      // ignore decoding error
    }
  }

  // 2. Check standard filename="..." or filename=...
  const stdMatch = disposition.match(/filename\s*=\s*(?:"([^"]+)"|'([^']+)'|([^;\s\r\n]+))/i);
  if (stdMatch) {
    let candidate = (stdMatch[1] || stdMatch[2] || stdMatch[3] || "").trim();

    // Check for RFC-2047 MIME encoded string: =?charset?Q?text?= or =?charset?B?text?=
    const rfc2047Match = candidate.match(/^=\?([^?]+)\?([QBqb])\?([^?]+)\?=$/);
    if (rfc2047Match) {
      const encoding = rfc2047Match[2].toUpperCase();
      const encodedText = rfc2047Match[3];
      if (encoding === "Q") {
        try {
          candidate = decodeURIComponent(
            encodedText.replace(/_/g, " ").replace(/=([0-9A-Fa-f]{2})/g, "%$1")
          );
        } catch {
          candidate = encodedText.replace(/_/g, " ");
        }
      } else if (encoding === "B") {
        try {
          if (typeof window !== "undefined" && window.atob) {
            candidate = window.atob(encodedText);
          }
        } catch {
          // ignore
        }
      }
    }

    candidate = candidate.replace(/^["']|["']$/g, "").trim();
    if (candidate) return candidate;
  }

  return null;
}

export function resolveExportFilename(
  disposition: string | undefined,
  reportTitle: string,
  format: "PDF" | "EXCEL" | "CSV"
): string {
  const targetExt = format === "PDF" ? "pdf" : format === "EXCEL" ? "xlsx" : "csv";
  let filename = parseFilenameFromContentDisposition(disposition);

  if (!filename) {
    let clean = reportTitle.trim();
    const extRegex = new RegExp(`\\.${targetExt}$`, "i");
    clean = clean.replace(extRegex, "");
    clean = clean.replace(/[^a-zA-Z0-9_-]/g, "_").replace(/_+/g, "_").replace(/^_+|_+$/g, "");
    if (!clean) clean = "Internship_Report";
    return `${clean}.${targetExt}`;
  }

  // Clean candidate: remove illegal characters / \ : * ? " < > |
  filename = filename.replace(/[/\\:*?"<>|]/g, "_").replace(/\s+/g, "_");

  // Fix corrupted extensions e.g. .pdf=, .pdf_, .xlsx=, etc.
  const corruptedExtRegex = new RegExp(`\\.${targetExt}[_=?].*$`, "i");
  if (corruptedExtRegex.test(filename)) {
    filename = filename.replace(corruptedExtRegex, `.${targetExt}`);
  }

  // If already ending in .targetExt (case-insensitive), ensure exact lowercase extension
  const validExtRegex = new RegExp(`\\.${targetExt}$`, "i");
  if (validExtRegex.test(filename)) {
    filename = filename.replace(validExtRegex, `.${targetExt}`);
  } else {
    // Strip other extension and append targetExt
    filename = filename.replace(/\.[a-zA-Z0-9]+$/, "");
    filename = `${filename}.${targetExt}`;
  }

  // Remove duplicate extension e.g. .pdf.pdf
  const duplicateExtRegex = new RegExp(`\\.${targetExt}\\.${targetExt}$`, "i");
  if (duplicateExtRegex.test(filename)) {
    filename = filename.replace(duplicateExtRegex, `.${targetExt}`);
  }

  return filename;
}

const DEFAULT_COLUMNS = [
  "studentName",
  "uid",
  "branch",
  "companyName",
  "internshipMode",
  "stipend",
  "annualCtc",
  "attendancePercentage",
];

const OPERATOR_LABELS: Record<string, string> = {
  EQUALS: "Equals (=)",
  NOT_EQUALS: "Does Not Equal (≠)",
  CONTAINS: "Contains",
  STARTS_WITH: "Starts With",
  ENDS_WITH: "Ends With",
  IS_EMPTY: "Is Empty / Unspecified",
  IS_NOT_EMPTY: "Is Not Empty / Specified",
  GREATER_THAN: "Greater Than (>)",
  GREATER_THAN_OR_EQUAL: "Greater Than or Equal (≥)",
  LESS_THAN: "Less Than (<)",
  LESS_THAN_OR_EQUAL: "Less Than or Equal (≤)",
  BETWEEN: "Between (Range)",
  IS: "Is",
  IS_NOT: "Is Not",
  IS_ANY_OF: "Is Any Of (Match Any)",
  IS_NONE_OF: "Is None Of (Exclude)",
  BEFORE: "Before Date",
  AFTER: "After Date",
  ON: "On (Exact Date)",
  YES: "Yes (True)",
  NO: "No (False)",
};

const STEPS = [
  { id: 1, title: "Report Details", subtitle: "Title, Session & Scope" },
  { id: 2, title: "Select Columns", subtitle: "Choose Display Fields" },
  { id: 3, title: "Apply Filters", subtitle: "Rules & Conditions" },
  { id: 4, title: "Group & Sort", subtitle: "Layout & Ordering" },
  { id: 5, title: "Preview & Export", subtitle: "Generate, PDF, Excel & CSV" },
];

const PRESETS = [
  { id: "COMPANY_ALL", label: "All Students by Company", title: "All Students Grouped by Company" },
  { id: "ATTENDANCE_LOW", label: "Attendance Below 75%", title: "Students with Attendance Below 75%" },
  { id: "HIGH_CTC", label: "CTC Above 6 LPA", title: "Students with Annual CTC Above 6 LPA" },
  { id: "REMOTE", label: "Remote Internships", title: "Students in Remote Internships" },
  { id: "HYBRID", label: "Hybrid Internships", title: "Students in Hybrid Internships" },
  { id: "PENDING_COMPLETION", label: "Pending Completion", title: "Students with Pending Internship Completions" },
];

export function CustomReportBuilder({ initialReport, onSaved }: CustomReportBuilderProps) {
  const { sessions, selected: defaultSessionId } = useAcademicSessions();

  // Step state (1 to 5)
  const [currentStep, setCurrentStep] = useState<number>(1);
  const [maxStepReached, setMaxStepReached] = useState<number>(1);

  // Catalog and Companies
  const [catalog, setCatalog] = useState<CatalogField[]>([]);
  const [companies, setCompanies] = useState<string[]>([]);
  const [loadingCatalog, setLoadingCatalog] = useState(true);

  // Form State
  const [reportTitle, setReportTitle] = useState("Internship Management Report");
  const [reportDescription, setReportDescription] = useState("");
  const [scope, setScope] = useState<"INTERNSHIP" | "STUDENT">("INTERNSHIP");
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [selectedColumns, setSelectedColumns] = useState<string[]>(DEFAULT_COLUMNS);
  const [groupBy, setGroupBy] = useState("companyName");
  const [sortBy, setSortBy] = useState("companyName");
  const [sortDirection, setSortDirection] = useState<"ASC" | "DESC">("ASC");
  const [filterGroup, setFilterGroup] = useState<FilterGroup>({
    conjunction: "AND",
    rules: [],
    groups: [],
  });

  // Preset Tracking
  const [activePreset, setActivePreset] = useState<string | null>(null);
  const [isPresetCustomized, setIsPresetCustomized] = useState(false);

  // UI state
  const [columnSearch, setColumnSearch] = useState("");
  const [previewData, setPreviewData] = useState<CustomReportResponse | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewPage, setPreviewPage] = useState(0);
  const [tableSearch, setTableSearch] = useState("");
  const [exportingFormat, setExportingFormat] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [exportError, setExportError] = useState("");
  const [savedSuccessNotice, setSavedSuccessNotice] = useState("");
  const [saveModalOpen, setSaveModalOpen] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [loadedSavedId, setLoadedSavedId] = useState<number | null>(null);

  // Live Matching Record Count
  const [liveCount, setLiveCount] = useState<number | null>(null);
  const [liveStudentsCount, setLiveStudentsCount] = useState<number | null>(null);
  const [liveInternshipsCount, setLiveInternshipsCount] = useState<number | null>(null);
  const [countLoading, setCountLoading] = useState(false);

  // Helper to mark customized configuration
  const markCustomized = useCallback(() => {
    if (activePreset) {
      setIsPresetCustomized(true);
    }
  }, [activePreset]);

  // Load Catalog and Companies
  useEffect(() => {
    let active = true;
    setLoadingCatalog(true);
    Promise.all([
      api.get<CatalogField[]>("/admin/reports/custom/catalog"),
      api.get<string[]>("/admin/reports/custom/companies"),
    ])
      .then(([catRes, compRes]) => {
        if (active) {
          setCatalog(catRes.data);
          setCompanies(compRes.data);
          setLoadingCatalog(false);
        }
      })
      .catch(() => {
        if (active) {
          setError("Failed to load reporting catalog fields.");
          setLoadingCatalog(false);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  // Update companies when sessionId changes
  useEffect(() => {
    const params = sessionId ? { academicSessionId: sessionId } : {};
    api
      .get<string[]>("/admin/reports/custom/companies", { params })
      .then(({ data }) => {
        setCompanies(data);
      })
      .catch(() => {});
  }, [sessionId]);

  // Set default academic session if available
  useEffect(() => {
    if (sessionId === null && defaultSessionId != null) {
      setSessionId(defaultSessionId);
    }
  }, [defaultSessionId, sessionId]);

  // Handle Loading Initial Saved Report
  useEffect(() => {
    if (!initialReport) return;
    try {
      setLoadedSavedId(initialReport.id);
      setReportTitle(initialReport.title);
      setReportDescription(initialReport.description || "");
      if (initialReport.scope === "STUDENT" || initialReport.scope === "INTERNSHIP") {
        setScope(initialReport.scope);
      }
      const parsed = JSON.parse(initialReport.configurationJson);
      if (Array.isArray(parsed.columns) && parsed.columns.length > 0) {
        setSelectedColumns(parsed.columns);
      }
      if (parsed.groupBy) setGroupBy(parsed.groupBy);
      if (parsed.sortBy) setSortBy(parsed.sortBy);
      if (parsed.sortDirection) setSortDirection(parsed.sortDirection);
      if (parsed.academicSessionId !== undefined) setSessionId(parsed.academicSessionId);
      if (parsed.filterGroup) setFilterGroup(parsed.filterGroup);

      setActivePreset(null);
      setIsPresetCustomized(false);
      setCurrentStep(5);
      setMaxStepReached(5);
    } catch {
      setError("Failed to parse saved report configuration.");
    }
  }, [initialReport]);

  // Debounced Live Record Count
  useEffect(() => {
    let active = true;
    const timer = setTimeout(async () => {
      setCountLoading(true);
      try {
        const payload: CustomReportRequest = {
          title: reportTitle.trim() || "Live Count",
          scope,
          academicSessionId: sessionId || undefined,
          columns: selectedColumns.length > 0 ? selectedColumns : ["studentName"],
          filterGroup:
            filterGroup.rules.length > 0 || (filterGroup.groups && filterGroup.groups.length > 0)
              ? filterGroup
              : undefined,
          format: "PREVIEW",
        };
        const { data } = await api.post<{
          totalRecords: number;
          totalStudents: number;
          totalInternships: number;
          scope: string;
        }>("/admin/reports/custom/count", payload);
        if (active) {
          setLiveCount(scope === "STUDENT" ? data.totalStudents : data.totalInternships);
          setLiveStudentsCount(data.totalStudents);
          setLiveInternshipsCount(data.totalInternships);
          setCountLoading(false);
        }
      } catch {
        if (active) setCountLoading(false);
      }
    }, 350);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [scope, sessionId, filterGroup, reportTitle, selectedColumns]);

  // Group catalog by category
  const categorizedColumns = useMemo(() => {
    const map: Record<string, CatalogField[]> = {};
    for (const f of catalog) {
      if (!map[f.category]) map[f.category] = [];
      map[f.category].push(f);
    }
    return map;
  }, [catalog]);

  // Filtered catalog for column picker
  const filteredCatalogCategories = useMemo(() => {
    if (!columnSearch.trim()) return categorizedColumns;
    const term = columnSearch.toLowerCase();
    const result: Record<string, CatalogField[]> = {};
    for (const [cat, fields] of Object.entries(categorizedColumns)) {
      const matching = fields.filter(
        f => f.label.toLowerCase().includes(term) || f.id.toLowerCase().includes(term)
      );
      if (matching.length > 0) result[cat] = matching;
    }
    return result;
  }, [categorizedColumns, columnSearch]);

  // Column reordering
  function moveColumn(index: number, direction: "UP" | "DOWN") {
    const targetIndex = direction === "UP" ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= selectedColumns.length) return;
    const updated = [...selectedColumns];
    const [moved] = updated.splice(index, 1);
    updated.splice(targetIndex, 0, moved);
    setSelectedColumns(updated);
    markCustomized();
  }

  function removeColumn(colId: string) {
    if (selectedColumns.length <= 1) {
      alert("At least one display column is required.");
      return;
    }
    setSelectedColumns(prev => prev.filter(c => c !== colId));
    markCustomized();
  }

  function toggleColumn(colId: string) {
    if (selectedColumns.includes(colId)) {
      removeColumn(colId);
    } else {
      setSelectedColumns(prev => [...prev, colId]);
      markCustomized();
    }
  }

  // --- Immutable Filter Tree Updaters ---
  function updateGroupConjunction(path: number[], newConjunction: "AND" | "OR") {
    const cloned = structuredClone(filterGroup);
    let curr: FilterGroup = cloned;
    for (const idx of path) {
      if (!curr.groups || !curr.groups[idx]) return;
      curr = curr.groups[idx];
    }
    curr.conjunction = newConjunction;
    setFilterGroup(cloned);
    markCustomized();
  }

  function addRuleToPath(path: number[]) {
    const cloned = structuredClone(filterGroup);
    let curr: FilterGroup = cloned;
    for (const idx of path) {
      if (!curr.groups || !curr.groups[idx]) return;
      curr = curr.groups[idx];
    }
    const firstField = catalog[0];
    const newRule: FilterRule = {
      field: firstField ? firstField.id : "companyName",
      operator: firstField?.allowedOperators[0] || "EQUALS",
      value: "",
    };
    curr.rules.push(newRule);
    setFilterGroup(cloned);
    markCustomized();
  }

  function removeRuleFromPath(path: number[], ruleIndex: number) {
    const cloned = structuredClone(filterGroup);
    let curr: FilterGroup = cloned;
    for (const idx of path) {
      if (!curr.groups || !curr.groups[idx]) return;
      curr = curr.groups[idx];
    }
    curr.rules.splice(ruleIndex, 1);
    setFilterGroup(cloned);
    markCustomized();
  }

  function updateRuleInPath(path: number[], ruleIndex: number, updater: (r: FilterRule) => void) {
    const cloned = structuredClone(filterGroup);
    let curr: FilterGroup = cloned;
    for (const idx of path) {
      if (!curr.groups || !curr.groups[idx]) return;
      curr = curr.groups[idx];
    }
    if (curr.rules[ruleIndex]) {
      updater(curr.rules[ruleIndex]);
      setFilterGroup(cloned);
      markCustomized();
    }
  }

  function addSubGroupToPath(path: number[]) {
    const cloned = structuredClone(filterGroup);
    let curr: FilterGroup = cloned;
    for (const idx of path) {
      if (!curr.groups || !curr.groups[idx]) return;
      curr = curr.groups[idx];
    }
    if (!curr.groups) curr.groups = [];
    curr.groups.push({
      conjunction: curr.conjunction === "AND" ? "OR" : "AND",
      rules: [],
      groups: [],
    });
    setFilterGroup(cloned);
    markCustomized();
  }

  function removeSubGroupFromPath(parentPath: number[], groupIndex: number) {
    const cloned = structuredClone(filterGroup);
    let curr: FilterGroup = cloned;
    for (const idx of parentPath) {
      if (!curr.groups || !curr.groups[idx]) return;
      curr = curr.groups[idx];
    }
    if (!curr.groups) return;
    curr.groups.splice(groupIndex, 1);
    setFilterGroup(cloned);
    markCustomized();
  }

  function clearAllFilters() {
    setFilterGroup({ conjunction: "AND", rules: [], groups: [] });
    markCustomized();
  }

  function resetBuilder() {
    if (confirm("Reset the report builder to default settings? Any unsaved edits will be discarded.")) {
      setReportTitle("Internship Management Report");
      setReportDescription("");
      setScope("INTERNSHIP");
      setSelectedColumns(DEFAULT_COLUMNS);
      setGroupBy("companyName");
      setSortBy("companyName");
      setSortDirection("ASC");
      setFilterGroup({ conjunction: "AND", rules: [], groups: [] });
      setPreviewData(null);
      setLoadedSavedId(null);
      setActivePreset(null);
      setIsPresetCustomized(false);
      setCurrentStep(1);
      setMaxStepReached(1);
    }
  }

  // Preset Quick Reports
  function applyPreset(presetType: string) {
    setActivePreset(presetType);
    setIsPresetCustomized(false);

    switch (presetType) {
      case "COMPANY_ALL":
        setReportTitle("All Students Grouped by Company");
        setScope("INTERNSHIP");
        setSelectedColumns(DEFAULT_COLUMNS);
        setGroupBy("companyName");
        setSortBy("studentName");
        setSortDirection("ASC");
        setFilterGroup({ conjunction: "AND", rules: [], groups: [] });
        break;
      case "ATTENDANCE_LOW":
        setReportTitle("Students with Attendance Below 75%");
        setScope("INTERNSHIP");
        setSelectedColumns(DEFAULT_COLUMNS);
        setGroupBy("companyName");
        setSortBy("attendancePercentage");
        setSortDirection("ASC");
        setFilterGroup({
          conjunction: "AND",
          rules: [{ field: "attendancePercentage", operator: "LESS_THAN", value: "75" }],
          groups: [],
        });
        break;
      case "HIGH_CTC":
        setReportTitle("Students with Annual CTC Above 6 LPA");
        setScope("INTERNSHIP");
        setSelectedColumns(DEFAULT_COLUMNS);
        setGroupBy("companyName");
        setSortBy("annualCtc");
        setSortDirection("DESC");
        setFilterGroup({
          conjunction: "AND",
          rules: [{ field: "annualCtc", operator: "GREATER_THAN_OR_EQUAL", value: "6.0" }],
          groups: [],
        });
        break;
      case "REMOTE":
        setReportTitle("Students in Remote Internships");
        setScope("INTERNSHIP");
        setSelectedColumns(DEFAULT_COLUMNS);
        setGroupBy("companyName");
        setSortBy("studentName");
        setSortDirection("ASC");
        setFilterGroup({
          conjunction: "AND",
          rules: [{ field: "internshipMode", operator: "EQUALS", value: "REMOTE" }],
          groups: [],
        });
        break;
      case "HYBRID":
        setReportTitle("Students in Hybrid Internships");
        setScope("INTERNSHIP");
        setSelectedColumns(DEFAULT_COLUMNS);
        setGroupBy("companyName");
        setSortBy("studentName");
        setSortDirection("ASC");
        setFilterGroup({
          conjunction: "AND",
          rules: [{ field: "internshipMode", operator: "EQUALS", value: "HYBRID" }],
          groups: [],
        });
        break;
      case "PENDING_COMPLETION":
        setReportTitle("Students with Pending Internship Completions");
        setScope("INTERNSHIP");
        setSelectedColumns(DEFAULT_COLUMNS);
        setGroupBy("companyName");
        setSortBy("studentName");
        setSortDirection("ASC");
        setFilterGroup({
          conjunction: "AND",
          rules: [{ field: "completionStatus", operator: "EQUALS", value: "PENDING" }],
          groups: [],
        });
        break;
      default:
        break;
    }
    setCurrentStep(5);
    setMaxStepReached(5);
  }

  // Generate Preview
  const fetchPreview = useCallback(async (pageOverride?: number) => {
    if (!reportTitle.trim()) {
      setError("Please enter a report title.");
      return;
    }
    if (selectedColumns.length === 0) {
      setError("Please select at least one report column.");
      return;
    }

    setPreviewLoading(true);
    setError("");
    const targetPage = pageOverride !== undefined ? pageOverride : previewPage;

    const payload: CustomReportRequest = {
      title: reportTitle.trim(),
      scope,
      academicSessionId: sessionId || undefined,
      columns: selectedColumns,
      groupBy: groupBy || "NONE",
      sortBy: sortBy || "companyName",
      sortDirection,
      filterGroup:
        filterGroup.rules.length > 0 || (filterGroup.groups && filterGroup.groups.length > 0)
          ? filterGroup
          : undefined,
      format: "PREVIEW",
      page: targetPage,
      size: 50,
      search: tableSearch.trim() || undefined,
    };

    try {
      const { data } = await api.post<CustomReportResponse>("/admin/reports/custom/preview", payload);
      setPreviewData(data);
      setPreviewPage(targetPage);
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const msg = err.response?.data?.message || "Failed to generate report preview.";
        setError(msg);
      } else {
        setError("An unexpected error occurred while generating the preview.");
      }
    } finally {
      setPreviewLoading(false);
    }
  }, [reportTitle, selectedColumns, previewPage, scope, sessionId, groupBy, sortBy, sortDirection, filterGroup, tableSearch]);

  // When step 5 is active and no preview exists, auto-fetch
  useEffect(() => {
    if (currentStep === 5 && !previewData && !previewLoading && !loadingCatalog) {
      fetchPreview(0);
    }
  }, [currentStep, previewData, previewLoading, loadingCatalog, fetchPreview]);

  // Exports
  async function triggerExport(format: "PDF" | "EXCEL" | "CSV") {
    if (!reportTitle.trim()) {
      const msg = "Please enter a report title before exporting.";
      setError(msg);
      setExportError(msg);
      return;
    }
    if (selectedColumns.length === 0) {
      const msg = "Please select at least one report column before exporting.";
      setError(msg);
      setExportError(msg);
      return;
    }
    if (previewData && previewData.totalRecords === 0) {
      const msg = "No records match the current report filters. Adjust your filters in Step 3 to include matching records before exporting.";
      setError(msg);
      setExportError(msg);
      return;
    }

    setExportingFormat(format);
    setError("");
    setExportError("");

    const payload: CustomReportRequest = {
      title: reportTitle.trim(),
      scope,
      academicSessionId: sessionId || undefined,
      columns: selectedColumns,
      groupBy: groupBy || "NONE",
      sortBy: sortBy || "companyName",
      sortDirection,
      filterGroup:
        filterGroup.rules.length > 0 || (filterGroup.groups && filterGroup.groups.length > 0)
          ? filterGroup
          : undefined,
      format,
    };

    try {
      const response = await api.post<Blob>("/admin/reports/custom/export", payload, {
        responseType: "blob",
        timeout: 180_000,
      });

      const mimeType =
        format === "PDF"
          ? "application/pdf"
          : format === "EXCEL"
          ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          : "text/csv; charset=utf-8";

      const blob = response.data instanceof Blob ? response.data : new Blob([response.data], { type: mimeType });

      // Extract and resolve clean, standard filename
      let disposition: string | undefined;
      if (response.headers) {
        const raw = (response.headers as Record<string, unknown>)["content-disposition"];
        if (typeof raw === "string") {
          disposition = raw;
        } else if (typeof (response.headers as { get?: (name: string) => unknown }).get === "function") {
          const val = (response.headers as { get: (name: string) => unknown }).get("content-disposition");
          if (typeof val === "string") disposition = val;
        }
      }

      const filename = resolveExportFilename(disposition, reportTitle, format);

      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.style.display = "none";
      link.href = url;
      link.setAttribute("download", filename);
      document.body.appendChild(link);
      link.click();
      window.setTimeout(() => {
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
      }, 60_000);
    } catch (err: unknown) {
      let errorMsg = `Failed to export ${format} report.`;
      if (axios.isAxiosError(err)) {
        const data = err.response?.data;
        if (data instanceof Blob) {
          try {
            const text = await data.text();
            const parsed = JSON.parse(text);
            errorMsg = parsed.message || parsed.error || errorMsg;
          } catch {
            // Not JSON
          }
        } else if (typeof data === "object" && data !== null && "message" in data) {
          errorMsg = String((data as { message: unknown }).message);
        } else if (err.message) {
          errorMsg = err.message;
        }
      } else if (err instanceof Error) {
        errorMsg = err.message;
      }
      setError(errorMsg);
      setExportError(errorMsg);
    } finally {
      setExportingFormat(null);
    }
  }

  // Save Report Configuration
  async function saveConfiguration() {
    if (!reportTitle.trim()) {
      alert("Please enter a report title.");
      return;
    }
    setSavingConfig(true);
    const configToSave = {
      columns: selectedColumns,
      groupBy,
      sortBy,
      sortDirection,
      academicSessionId: sessionId,
      filterGroup,
    };

    const payload = {
      title: reportTitle.trim(),
      description: reportDescription.trim() || undefined,
      scope,
      configurationJson: JSON.stringify(configToSave),
    };

    try {
      if (loadedSavedId) {
        await api.put(`/admin/reports/saved/${loadedSavedId}`, payload);
        setSavedSuccessNotice(`Updated saved configuration "${reportTitle}"`);
      } else {
        const { data } = await api.post<SavedReportDto>("/admin/reports/saved", payload);
        setLoadedSavedId(data.id);
        setSavedSuccessNotice(`Report configuration "${reportTitle}" saved successfully`);
      }
      setSaveModalOpen(false);
      if (onSaved) onSaved();
      setTimeout(() => setSavedSuccessNotice(""), 5000);
    } catch {
      alert("Failed to save report configuration.");
    } finally {
      setSavingConfig(false);
    }
  }

  // Step Navigation Handlers
  function goToStep(target: number) {
    if (target < 1 || target > 5) return;
    if (target > currentStep) {
      // Validate current step before advancing
      if (currentStep === 1 && !reportTitle.trim()) {
        setError("Please enter a report title to proceed.");
        return;
      }
      if (currentStep === 2 && selectedColumns.length === 0) {
        setError("Please select at least one column to proceed.");
        return;
      }
    }
    setError("");
    setCurrentStep(target);
    if (target > maxStepReached) setMaxStepReached(target);
  }

  function handleNext() {
    if (currentStep === 1) {
      if (!reportTitle.trim()) {
        setError("Please enter a report title.");
        return;
      }
      goToStep(2);
    } else if (currentStep === 2) {
      if (selectedColumns.length === 0) {
        setError("Please select at least one column.");
        return;
      }
      goToStep(3);
    } else if (currentStep === 3) {
      goToStep(4);
    } else if (currentStep === 4) {
      goToStep(5);
      fetchPreview(0);
    }
  }

  function handleBack() {
    if (currentStep > 1) {
      goToStep(currentStep - 1);
    }
  }

  // Build human-readable filter explanation for summary card
  function formatGroupReadable(group: FilterGroup): string {
    const parts: string[] = [];
    for (const rule of group.rules) {
      const fieldDef = catalog.find(f => f.id === rule.field);
      const label = fieldDef?.label || rule.field;
      const op = OPERATOR_LABELS[rule.operator] || rule.operator;
      if (rule.operator === "IS_EMPTY" || rule.operator === "IS_NOT_EMPTY") {
        parts.push(`${label} ${op}`);
      } else if (rule.operator === "BETWEEN") {
        parts.push(`${label} is between "${rule.value || ""}" and "${rule.secondaryValue || ""}"`);
      } else if (rule.operator === "IS_ANY_OF" || rule.operator === "IS_NONE_OF") {
        const valList = (rule.values || []).join(", ");
        parts.push(`${label} ${op.toLowerCase()} [${valList}]`);
      } else {
        parts.push(`${label} ${op} "${rule.value || ""}"`);
      }
    }
    if (group.groups) {
      for (const sub of group.groups) {
        const subStr = formatGroupReadable(sub);
        if (subStr) parts.push(`(${subStr})`);
      }
    }
    return parts.join(` ${group.conjunction} `);
  }

  // --- Recursive Filter Group Renderer ---
  function renderFilterGroup(group: FilterGroup, path: number[] = [], depth = 0) {
    const isRoot = depth === 0;
    const isAnd = group.conjunction === "AND";
    const isOr = group.conjunction === "OR";
    const hasRules = group.rules.length > 0;
    const hasSubgroups = Boolean(group.groups && group.groups.length > 0);
    const isEmpty = !hasRules && !hasSubgroups;
    const isSingleCondition = group.rules.length === 1 && !hasSubgroups;

    return (
      <div
        key={`filter-group-${path.join("-") || "root"}`}
        className={`rounded-2xl border transition-all ${
          isRoot
            ? "border-slate-200 dark:border-slate-800 bg-slate-50/70 dark:bg-slate-900/50 p-4"
            : "border-blue-200 dark:border-blue-900/70 bg-white dark:bg-slate-800/90 mt-3 p-3.5 shadow-2xs"
        }`}
      >
        {/* Group Header: AND/OR Selector and explanations */}
        <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-200/80 dark:border-slate-700/60">
          <div className="flex flex-wrap items-center gap-2.5">
            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">
              Condition Match:
            </span>

            {/* AND/OR Toggle with explicit light-blue selected / neutral inactive styling */}
            <div className="inline-flex rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-0.5 shadow-2xs">
              <button
                type="button"
                onClick={() => updateGroupConjunction(path, "AND")}
                aria-pressed={isAnd}
                className={`rounded-md px-3 py-1 text-xs transition font-semibold ${
                  isAnd
                    ? "bg-blue-100 dark:bg-blue-900/60 text-blue-900 dark:text-blue-100 border border-blue-300 dark:border-blue-700 shadow-2xs"
                    : "bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-transparent hover:bg-slate-50 dark:hover:bg-slate-700"
                }`}
              >
                AND (All Must Match)
              </button>

              <button
                type="button"
                onClick={() => updateGroupConjunction(path, "OR")}
                aria-pressed={isOr}
                className={`rounded-md px-3 py-1 text-xs transition font-semibold ${
                  isOr
                    ? "bg-blue-100 dark:bg-blue-900/60 text-blue-900 dark:text-blue-100 border border-blue-300 dark:border-blue-700 shadow-2xs"
                    : "bg-white dark:bg-slate-800 text-slate-600 dark:text-slate-400 border border-transparent hover:bg-slate-50 dark:hover:bg-slate-700"
                }`}
              >
                OR (Any Can Match)
              </button>
            </div>

            {/* Beginner-friendly explanation */}
            <span className="text-[11px] text-slate-500 dark:text-slate-400 italic">
              {isAnd
                ? "Include records only when every condition matches."
                : "Include records when at least one condition matches."}
            </span>

            {!isRoot && (
              <span className="rounded bg-blue-50 dark:bg-blue-950/70 border border-blue-200 dark:border-blue-800 px-2 py-0.5 text-[10px] font-bold text-blue-700 dark:text-blue-300">
                Nested Group (Level {depth})
              </span>
            )}
          </div>

          {/* Group Action Buttons */}
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => addRuleToPath(path)}
              className="inline-flex items-center gap-1 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 px-2.5 py-1 text-xs font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 transition shadow-2xs"
            >
              <span>+</span>
              <span>Add Condition</span>
            </button>

            {depth < 2 && (
              <button
                type="button"
                onClick={() => addSubGroupToPath(path)}
                className="inline-flex items-center gap-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-2.5 py-1 text-xs font-medium text-slate-700 dark:text-slate-300 hover:bg-slate-50 transition shadow-2xs"
              >
                <span>+</span>
                <span>Add Nested Group</span>
              </button>
            )}

            {!isRoot && (
              <button
                type="button"
                onClick={() => {
                  const parentPath = path.slice(0, -1);
                  const groupIndex = path[path.length - 1];
                  removeSubGroupFromPath(parentPath, groupIndex);
                }}
                className="rounded-lg border border-red-200 dark:border-red-900/60 px-2 py-1 text-xs text-red-600 hover:bg-red-50 dark:hover:bg-red-950/50 transition"
              >
                Remove Group
              </button>
            )}
          </div>
        </div>

        {/* Empty & Single Condition Explanations */}
        {isEmpty && (
          <p className="mt-3 text-xs text-slate-400 italic py-2 text-center bg-white/60 dark:bg-slate-900/40 rounded-lg border border-dashed border-slate-200 dark:border-slate-800">
            No conditions in this group yet. Click &quot;+ Add Condition&quot; above to filter records.
          </p>
        )}
        {isSingleCondition && (
          <p className="mt-2 text-[11px] text-slate-400 italic">
            Single condition active. Conjunction applies when multiple conditions are added.
          </p>
        )}

        {/* Filter Rules List */}
        <div className="mt-3 space-y-2">
          {group.rules.map((rule, ruleIdx) => {
            const fieldDef = catalog.find(f => f.id === rule.field);
            const isNoValueOp = rule.operator === "IS_EMPTY" || rule.operator === "IS_NOT_EMPTY";
            const isBetweenOp = rule.operator === "BETWEEN";
            const isMultiOp = rule.operator === "IS_ANY_OF" || rule.operator === "IS_NONE_OF";

            return (
              <div
                key={`rule-${path.join("-")}-${ruleIdx}`}
                className="flex flex-wrap items-center gap-2 rounded-xl border border-slate-200/90 dark:border-slate-700/80 bg-white dark:bg-slate-800/95 p-2.5 text-xs shadow-2xs"
              >
                {/* 1. Field Selector */}
                <div className="min-w-[170px]">
                  <select
                    value={rule.field}
                    onChange={e => {
                      const newFieldId = e.target.value;
                      const newFieldDef = catalog.find(f => f.id === newFieldId);
                      updateRuleInPath(path, ruleIdx, r => {
                        r.field = newFieldId;
                        r.operator = newFieldDef?.allowedOperators[0] || "EQUALS";
                        r.value = "";
                        r.secondaryValue = "";
                        r.values = [];
                      });
                    }}
                    className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100 font-medium focus:ring-1 focus:ring-blue-500"
                  >
                    {Object.entries(categorizedColumns).map(([catName, fields]) => (
                      <optgroup key={catName} label={catName}>
                        {fields.map(f => (
                          <option key={f.id} value={f.id}>
                            {f.label}
                          </option>
                        ))}
                      </optgroup>
                    ))}
                  </select>
                </div>

                {/* 2. Operator Selector */}
                <div className="min-w-[150px]">
                  <select
                    value={rule.operator}
                    onChange={e => {
                      const newOp = e.target.value;
                      updateRuleInPath(path, ruleIdx, r => {
                        r.operator = newOp;
                      });
                    }}
                    className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100 font-medium focus:ring-1 focus:ring-blue-500"
                  >
                    {(fieldDef?.allowedOperators || ["EQUALS"]).map(op => (
                      <option key={op} value={op}>
                        {OPERATOR_LABELS[op] || op}
                      </option>
                    ))}
                  </select>
                </div>

                {/* 3. Smart Value Inputs */}
                {!isNoValueOp && (
                  <div className="flex flex-wrap items-center gap-1.5 flex-1 min-w-[200px]">
                    {/* Dynamic Company Selection */}
                    {fieldDef?.dynamicCompany ? (
                      isMultiOp ? (
                        <div className="flex flex-wrap items-center gap-1 flex-1">
                          <select
                            onChange={e => {
                              const val = e.target.value;
                              if (!val) return;
                              updateRuleInPath(path, ruleIdx, r => {
                                if (!r.values) r.values = [];
                                if (!r.values.includes(val)) r.values.push(val);
                              });
                              e.target.value = "";
                            }}
                            className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                          >
                            <option value="">+ Add company to match…</option>
                            {companies
                              .filter(c => !(rule.values || []).includes(c))
                              .map(c => (
                                <option key={c} value={c}>
                                  {c}
                                </option>
                              ))}
                          </select>
                          {(rule.values || []).map(val => (
                            <span
                              key={val}
                              className="inline-flex items-center gap-1 rounded-md bg-blue-100 text-blue-900 dark:bg-blue-900/60 dark:text-blue-200 px-2 py-0.5 text-[11px] font-medium"
                            >
                              {val}
                              <button
                                type="button"
                                onClick={() => {
                                  updateRuleInPath(path, ruleIdx, r => {
                                    r.values = (r.values || []).filter(v => v !== val);
                                  });
                                }}
                                className="hover:text-red-600 font-bold ml-0.5"
                                title="Remove company"
                              >
                                ×
                              </button>
                            </span>
                          ))}
                        </div>
                      ) : (
                        <div className="flex-1">
                          <input
                            list="companies-datalist"
                            placeholder="Type or select company…"
                            value={rule.value || ""}
                            onChange={e => {
                              const val = e.target.value;
                              updateRuleInPath(path, ruleIdx, r => {
                                r.value = val;
                              });
                            }}
                            className="w-full rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100 focus:ring-1 focus:ring-blue-500"
                          />
                          <datalist id="companies-datalist">
                            {companies.map(c => (
                              <option key={c} value={c} />
                            ))}
                          </datalist>
                        </div>
                      )
                    ) : fieldDef?.kind === "CATEGORICAL" && fieldDef.choices ? (
                      isMultiOp ? (
                        <div className="flex flex-wrap items-center gap-1 flex-1">
                          <select
                            onChange={e => {
                              const val = e.target.value;
                              if (!val) return;
                              updateRuleInPath(path, ruleIdx, r => {
                                if (!r.values) r.values = [];
                                if (!r.values.includes(val)) r.values.push(val);
                              });
                              e.target.value = "";
                            }}
                            className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                          >
                            <option value="">+ Add choice…</option>
                            {fieldDef.choices
                              .filter(c => !(rule.values || []).includes(c.value))
                              .map(c => (
                                <option key={c.value} value={c.value}>
                                  {c.label}
                                </option>
                              ))}
                          </select>
                          {(rule.values || []).map(val => {
                            const lbl = fieldDef.choices?.find(c => c.value === val)?.label || val;
                            return (
                              <span
                                key={val}
                                className="inline-flex items-center gap-1 rounded-md bg-blue-100 text-blue-900 dark:bg-blue-900/60 dark:text-blue-200 px-2 py-0.5 text-[11px] font-medium"
                              >
                                {lbl}
                                <button
                                  type="button"
                                  onClick={() => {
                                    updateRuleInPath(path, ruleIdx, r => {
                                      r.values = (r.values || []).filter(v => v !== val);
                                    });
                                  }}
                                  className="hover:text-red-600 font-bold ml-0.5"
                                  title="Remove"
                                >
                                  ×
                                </button>
                              </span>
                            );
                          })}
                        </div>
                      ) : (
                        <select
                          value={rule.value || ""}
                          onChange={e => {
                            const val = e.target.value;
                            updateRuleInPath(path, ruleIdx, r => {
                              r.value = val;
                            });
                          }}
                          className="flex-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                        >
                          <option value="">Select option…</option>
                          {fieldDef.choices.map(c => (
                            <option key={c.value} value={c.value}>
                              {c.label}
                            </option>
                          ))}
                        </select>
                      )
                    ) : fieldDef?.kind === "BOOLEAN" ? (
                      <div className="flex items-center gap-2">
                        <label className="inline-flex items-center gap-1 cursor-pointer">
                          <input
                            type="radio"
                            name={`bool-${path.join("-")}-${ruleIdx}`}
                            checked={rule.operator === "YES"}
                            onChange={() => {
                              updateRuleInPath(path, ruleIdx, r => {
                                r.operator = "YES";
                                r.value = "true";
                              });
                            }}
                            className="text-blue-600"
                          />
                          <span className="font-semibold text-slate-700 dark:text-slate-200">Yes / True</span>
                        </label>
                        <label className="inline-flex items-center gap-1 cursor-pointer ml-2">
                          <input
                            type="radio"
                            name={`bool-${path.join("-")}-${ruleIdx}`}
                            checked={rule.operator === "NO"}
                            onChange={() => {
                              updateRuleInPath(path, ruleIdx, r => {
                                r.operator = "NO";
                                r.value = "false";
                              });
                            }}
                            className="text-blue-600"
                          />
                          <span className="font-semibold text-slate-700 dark:text-slate-200">No / False</span>
                        </label>
                      </div>
                    ) : fieldDef?.kind === "DATE" || fieldDef?.kind === "DATETIME" ? (
                      isBetweenOp ? (
                        <div className="flex items-center gap-1.5 flex-1">
                          <input
                            type="date"
                            value={rule.value || ""}
                            onChange={e => {
                              const val = e.target.value;
                              updateRuleInPath(path, ruleIdx, r => {
                                r.value = val;
                              });
                            }}
                            className="flex-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                          />
                          <span className="text-slate-400 font-medium">to</span>
                          <input
                            type="date"
                            value={rule.secondaryValue || ""}
                            onChange={e => {
                              const val = e.target.value;
                              updateRuleInPath(path, ruleIdx, r => {
                                r.secondaryValue = val;
                              });
                            }}
                            className="flex-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                          />
                        </div>
                      ) : (
                        <input
                          type="date"
                          value={rule.value || ""}
                          onChange={e => {
                            const val = e.target.value;
                            updateRuleInPath(path, ruleIdx, r => {
                              r.value = val;
                            });
                          }}
                          className="flex-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                        />
                      )
                    ) : fieldDef?.kind === "NUMBER" ? (
                      isBetweenOp ? (
                        <div className="flex items-center gap-1.5 flex-1">
                          <input
                            type="number"
                            step="any"
                            placeholder="Minimum Value"
                            value={rule.value || ""}
                            onChange={e => {
                              const val = e.target.value;
                              updateRuleInPath(path, ruleIdx, r => {
                                r.value = val;
                              });
                            }}
                            className="w-32 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                          />
                          <span className="text-slate-400 font-medium">to</span>
                          <input
                            type="number"
                            step="any"
                            placeholder="Maximum Value"
                            value={rule.secondaryValue || ""}
                            onChange={e => {
                              const val = e.target.value;
                              updateRuleInPath(path, ruleIdx, r => {
                                r.secondaryValue = val;
                              });
                            }}
                            className="w-32 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                          />
                        </div>
                      ) : (
                        <input
                          type="number"
                          step="any"
                          placeholder="Enter numeric value…"
                          value={rule.value || ""}
                          onChange={e => {
                            const val = e.target.value;
                            updateRuleInPath(path, ruleIdx, r => {
                              r.value = val;
                            });
                          }}
                          className="flex-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                        />
                      )
                    ) : (
                      <input
                        type="text"
                        placeholder="Value to match…"
                        value={rule.value || ""}
                        onChange={e => {
                          const val = e.target.value;
                          updateRuleInPath(path, ruleIdx, r => {
                            r.value = val;
                          });
                        }}
                        className="flex-1 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-2.5 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                      />
                    )}
                  </div>
                )}

                {/* 4. Remove Control */}
                <button
                  type="button"
                  onClick={() => removeRuleFromPath(path, ruleIdx)}
                  title="Remove condition"
                  aria-label="Remove condition"
                  className="rounded p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 transition"
                >
                  <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                </button>
              </div>
            );
          })}
        </div>

        {/* Subgroups */}
        {group.groups && group.groups.length > 0 && (
          <div className="mt-3 space-y-3 pl-3 border-l-2 border-blue-300 dark:border-blue-700">
            {group.groups.map((sub, sIdx) => renderFilterGroup(sub, [...path, sIdx], depth + 1))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Notifications */}
      {savedSuccessNotice && (
        <div className="rounded-xl bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-200 dark:border-emerald-800 p-3.5 text-xs text-emerald-800 dark:text-emerald-200 flex items-center justify-between">
          <span>{savedSuccessNotice}</span>
          <button onClick={() => setSavedSuccessNotice("")} className="text-emerald-600 font-bold ml-2">
            ×
          </button>
        </div>
      )}
      {error && (
        <div className="rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 p-3.5 text-xs text-red-700 dark:text-red-300 flex items-center justify-between">
          <span>{error}</span>
          <button onClick={() => setError("")} className="text-red-600 font-bold ml-2">
            ×
          </button>
        </div>
      )}

      {/* Quick Report Presets Control */}
      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-3.5 shadow-xs">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
              Quick Report Presets:
            </span>
            <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Quick Report Presets">
              {PRESETS.map(preset => {
                const isSelected = activePreset === preset.id;
                return (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => applyPreset(preset.id)}
                    className={`rounded-lg px-2.5 py-1 text-xs font-medium transition border flex items-center gap-1.5 ${
                      isSelected
                        ? "bg-blue-50 dark:bg-blue-950/50 border-blue-300 dark:border-blue-700 text-blue-900 dark:text-blue-100 font-semibold shadow-2xs"
                        : "bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700"
                    }`}
                  >
                    {isSelected && (
                      <span className="h-1.5 w-1.5 rounded-full bg-blue-600 inline-block" aria-hidden="true" />
                    )}
                    <span>{preset.label}</span>
                    {isSelected && isPresetCustomized && (
                      <span className="text-[10px] text-amber-700 dark:text-amber-400 font-normal">
                        (Customized)
                      </span>
                    )}
                  </button>
                );
              })}
            </div>
          </div>

          <button
            type="button"
            onClick={resetBuilder}
            className="rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3 py-1 text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 transition shadow-2xs"
          >
            Reset Builder
          </button>
        </div>
      </div>

      {/* 5-Step Stepper Navigation */}
      <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 px-4 py-3 shadow-xs">
        <nav aria-label="Report Builder Steps">
          <ol className="flex flex-wrap items-center justify-between gap-2">
            {STEPS.map(step => {
              const isActive = currentStep === step.id;
              const isCompleted = step.id < currentStep;
              const isAccessible = step.id <= maxStepReached;

              return (
                <li key={step.id} className="flex-1 min-w-[135px]">
                  <button
                    type="button"
                    disabled={!isAccessible}
                    onClick={() => goToStep(step.id)}
                    aria-current={isActive ? "step" : undefined}
                    className={`w-full text-left p-2 rounded-xl transition flex items-center gap-2.5 ${
                      isActive
                        ? "bg-blue-50/80 dark:bg-blue-950/40 border border-blue-200 dark:border-blue-800 shadow-2xs"
                        : isCompleted
                        ? "bg-transparent hover:bg-slate-50 dark:hover:bg-slate-800/60 border border-transparent"
                        : "bg-transparent opacity-50 cursor-not-allowed border border-transparent"
                    }`}
                  >
                    <span
                      className={`h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
                        isActive
                          ? "bg-blue-600 text-white shadow-2xs"
                          : isCompleted
                          ? "bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300 border border-emerald-300 dark:border-emerald-800"
                          : "bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400"
                      }`}
                    >
                      {isCompleted ? "✓" : step.id}
                    </span>
                    <div className="truncate">
                      <span
                        className={`block text-xs truncate ${
                          isActive
                            ? "font-bold text-blue-950 dark:text-blue-100"
                            : isCompleted
                            ? "font-semibold text-slate-800 dark:text-slate-200"
                            : "font-medium text-slate-500 dark:text-slate-400"
                        }`}
                      >
                        {step.title}
                      </span>
                      <span className="block text-[10px] text-slate-400 dark:text-slate-500 truncate">
                        {step.subtitle}
                      </span>
                    </div>
                  </button>
                </li>
              );
            })}
          </ol>
        </nav>
      </div>

      {/* Live Matching Count Banner */}
      <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-2.5 rounded-xl bg-slate-50 dark:bg-slate-800/60 border border-slate-200 dark:border-slate-700 text-xs">
        <div className="flex items-center gap-2">
          <span className="h-2 w-2 rounded-full bg-blue-600" />
          <span className="font-semibold text-slate-800 dark:text-slate-200">
            {countLoading ? (
              <span className="text-slate-500 italic">Calculating matching records…</span>
            ) : liveCount !== null ? (
              <span>
                Matching current filters:{" "}
                <strong className="text-blue-700 dark:text-blue-300">
                  {scope === "STUDENT"
                    ? `${liveStudentsCount ?? liveCount} students`
                    : `${liveInternshipsCount ?? liveCount} internship records`}
                </strong>
                {scope === "INTERNSHIP" && liveStudentsCount !== null && (
                  <span className="text-slate-500 ml-1">({liveStudentsCount} unique students)</span>
                )}
              </span>
            ) : (
              <span className="text-slate-500">Live count active</span>
            )}
          </span>
        </div>

        <div className="text-[11px] text-slate-500">
          Academic Session:{" "}
          <strong>
            {sessionId
              ? sessions.find(s => s.id === sessionId)?.name || `Session #${sessionId}`
              : "All Academic Sessions"}
          </strong>{" "}
          · Scope: <strong>{scope === "STUDENT" ? "Student-wise" : "Internship-wise"}</strong>
        </div>
      </div>

      {/* ================= STEP 1: REPORT DETAILS ================= */}
      {currentStep === 1 && (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-6">
          {/* Institutional Header Card Preview */}
          <div className="rounded-xl bg-slate-50 dark:bg-slate-800/60 p-4 border border-slate-200/80 dark:border-slate-700/80 text-center">
            <h1 className="text-sm font-black tracking-wide text-slate-900 dark:text-slate-100 uppercase">
              St. Vincent Pallotti College of Engineering and Technology
            </h1>
            <h2 className="text-xs font-semibold text-slate-600 dark:text-slate-400 uppercase mt-0.5">
              Department of Computer Engineering
            </h2>
            <div className="my-1.5 inline-block px-2.5 py-0.5 bg-slate-200 dark:bg-slate-700 rounded text-[10px] font-bold tracking-widest text-slate-800 dark:text-slate-200 uppercase">
              INTERNSHIP MANAGEMENT REPORT
            </div>
            <p className="text-[11px] text-slate-500 italic">
              Official institutional header will appear automatically on all previews and generated PDF / Excel / CSV exports.
            </p>
          </div>

          <div className="pb-3 border-b border-slate-100 dark:border-slate-800">
            <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
              Step 1: Configure Report Details
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Enter your customized report title, choose the academic session, and select the record scope.
            </p>
          </div>

          <div className="grid gap-5 sm:grid-cols-2">
            {/* Report Title */}
            <div className="sm:col-span-2">
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Report Title <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                required
                value={reportTitle}
                onChange={e => {
                  setReportTitle(e.target.value);
                  markCustomized();
                }}
                placeholder="e.g. Low Attendance Student Summary - Fall 2026"
                className="mt-1 w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2 text-sm text-slate-800 dark:text-slate-100 font-semibold focus:ring-2 focus:ring-blue-500"
              />
              <p className="mt-1 text-[11px] text-slate-400">
                This title will appear prominently as the main heading in generated exports.
              </p>
            </div>

            {/* Academic Session */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Academic Session
              </label>
              <select
                value={sessionId ?? ""}
                onChange={e => {
                  setSessionId(e.target.value ? Number(e.target.value) : null);
                  markCustomized();
                }}
                className="mt-1 w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2 text-xs text-slate-800 dark:text-slate-100 focus:ring-2 focus:ring-blue-500"
              >
                <option value="">All Academic Sessions</option>
                {sessions.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.name} {s.active ? "(Active Session)" : ""}
                  </option>
                ))}
              </select>
              <p className="mt-1 text-[11px] text-slate-400">
                Limits data to records belonging to the selected academic session.
              </p>
            </div>

            {/* Report Scope (Dual Internships) */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Report Scope (Dual Internships)
              </label>
              <div className="mt-1 inline-flex w-full rounded-xl border border-slate-200 dark:border-slate-700 p-0.5 bg-white dark:bg-slate-900">
                <button
                  type="button"
                  onClick={() => {
                    setScope("INTERNSHIP");
                    markCustomized();
                  }}
                  className={`flex-1 rounded-lg py-1.5 px-2 text-xs font-semibold transition ${
                    scope === "INTERNSHIP"
                      ? "bg-blue-100 dark:bg-blue-900/60 text-blue-900 dark:text-blue-100 border border-blue-300 dark:border-blue-700 shadow-2xs"
                      : "text-slate-600 dark:text-slate-400 hover:text-slate-900"
                  }`}
                >
                  Internship-wise
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setScope("STUDENT");
                    markCustomized();
                  }}
                  className={`flex-1 rounded-lg py-1.5 px-2 text-xs font-semibold transition ${
                    scope === "STUDENT"
                      ? "bg-blue-100 dark:bg-blue-900/60 text-blue-900 dark:text-blue-100 border border-blue-300 dark:border-blue-700 shadow-2xs"
                      : "text-slate-600 dark:text-slate-400 hover:text-slate-900"
                  }`}
                >
                  Student-wise
                </button>
              </div>
              <p className="mt-1.5 text-[11px] text-blue-800 dark:text-blue-300 font-medium bg-blue-50/70 dark:bg-blue-950/40 p-2 rounded-lg border border-blue-100 dark:border-blue-900/50">
                {scope === "STUDENT"
                  ? "Student-wise: One record per student."
                  : "Internship-wise: One record per internship. Students with two internships may appear twice."}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ================= STEP 2: SELECT & ORDER REPORT COLUMNS ================= */}
      {currentStep === 2 && (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-5">
          {/* Header & Instructions */}
          <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-100 dark:border-slate-800">
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
                  Step 2: Select & Order Report Columns
                </h2>
                <span className="rounded-full bg-blue-100 dark:bg-blue-900/50 text-blue-800 dark:text-blue-200 px-2.5 py-0.5 text-xs font-bold">
                  {selectedColumns.length} columns selected
                </span>
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Choose which fields appear in the report and arrange their order.
              </p>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => {
                  setSelectedColumns(catalog.map(f => f.id));
                  markCustomized();
                }}
                className="text-xs text-blue-600 dark:text-blue-400 font-semibold hover:underline"
              >
                Select All
              </button>
              <span className="text-slate-300 dark:text-slate-700">|</span>
              <button
                type="button"
                onClick={() => {
                  setSelectedColumns(DEFAULT_COLUMNS);
                  markCustomized();
                }}
                className="text-xs text-slate-500 hover:underline"
              >
                Reset Default
              </button>
            </div>
          </div>

          {/* Two-Column Side-by-Side Area on Wide Screens, Stacked on Mobile */}
          <div className="grid gap-6 lg:grid-cols-12 items-start">
            {/* Left Area: Available Fields (7 columns) */}
            <div className="lg:col-span-7 space-y-3.5">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
                    Available Fields
                  </h3>
                  <p className="text-[11px] text-slate-500 dark:text-slate-400">
                    Check a field to include it in the report; uncheck to exclude it.
                  </p>
                </div>

                <input
                  type="text"
                  placeholder="Search available fields…"
                  value={columnSearch}
                  onChange={e => setColumnSearch(e.target.value)}
                  className="w-full sm:w-56 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-800 dark:text-slate-100 placeholder:text-slate-400 focus:ring-1 focus:ring-blue-500"
                />
              </div>

              {/* Categorized Fields List */}
              <div className="space-y-3 max-h-[560px] overflow-y-auto pr-1">
                {Object.entries(filteredCatalogCategories).length === 0 ? (
                  <div className="rounded-xl border border-dashed border-slate-200 dark:border-slate-700 p-6 text-center text-xs text-slate-400">
                    No fields match &quot;{columnSearch}&quot;
                  </div>
                ) : (
                  Object.entries(filteredCatalogCategories).map(([catName, fields]) => (
                    <div
                      key={catName}
                      className="rounded-xl border border-slate-200/80 dark:border-slate-700/80 bg-white dark:bg-slate-800/60 p-3.5 shadow-2xs"
                    >
                      <div className="flex items-center justify-between mb-2 pb-1.5 border-b border-slate-100 dark:border-slate-700/60">
                        <span className="text-xs font-bold text-slate-700 dark:text-slate-200 uppercase tracking-wider">
                          {catName}
                        </span>
                        <span className="text-[10px] text-slate-400">
                          {fields.filter(f => selectedColumns.includes(f.id)).length} / {fields.length} selected
                        </span>
                      </div>

                      <div className="grid gap-2 sm:grid-cols-2">
                        {fields.map(f => {
                          const isChecked = selectedColumns.includes(f.id);
                          return (
                            <label
                              key={f.id}
                              className={`flex items-start gap-2.5 p-2 rounded-lg border transition cursor-pointer text-xs ${
                                isChecked
                                  ? "bg-blue-50/70 dark:bg-blue-950/40 border-blue-200 dark:border-blue-900/60 text-blue-950 dark:text-blue-100"
                                  : "bg-white dark:bg-slate-800 border-slate-100 dark:border-slate-700/60 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700/50"
                              }`}
                            >
                              <input
                                type="checkbox"
                                checked={isChecked}
                                onChange={() => toggleColumn(f.id)}
                                className="mt-0.5 rounded border-slate-300 text-blue-600 focus:ring-blue-500 shrink-0"
                              />
                              <div className="flex-1 min-w-0">
                                <span className={`block leading-snug break-words ${isChecked ? "font-bold text-blue-900 dark:text-blue-200" : "font-medium"}`}>
                                  {f.label}
                                </span>
                              </div>
                            </label>
                          );
                        })}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* Right Area: Selected Columns (5 columns) */}
            <div className="lg:col-span-5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50/70 dark:bg-slate-800/40 p-4 space-y-3 shadow-2xs">
              <div className="border-b border-slate-200/80 dark:border-slate-700/80 pb-2.5">
                <div className="flex items-center justify-between">
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-slate-200">
                    Selected Columns ({selectedColumns.length})
                  </h3>
                  <span className="text-[10px] text-slate-500 font-medium">
                    Order matters
                  </span>
                </div>
                <p className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                  Top to bottom = left to right in the exported report.
                </p>
              </div>

              {/* Single Column Compact Ordered List */}
              <div className="space-y-1.5 max-h-[500px] overflow-y-auto pr-1">
                {selectedColumns.length === 0 ? (
                  <p className="text-xs text-slate-400 italic py-8 text-center">
                    No columns selected. Check fields from the left to include them.
                  </p>
                ) : (
                  selectedColumns.map((colId, idx) => {
                    const def = catalog.find(f => f.id === colId);
                    const isFirst = idx === 0;
                    const isLast = idx === selectedColumns.length - 1;

                    return (
                      <div
                        key={colId}
                        className="flex items-center justify-between gap-2.5 rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-2.5 text-xs shadow-2xs transition hover:border-slate-300 dark:hover:border-slate-600"
                      >
                        {/* Position Number & Complete Readable Name */}
                        <div className="flex items-center gap-2.5 min-w-0 flex-1">
                          <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded bg-slate-100 dark:bg-slate-700 font-mono text-[11px] font-bold text-slate-600 dark:text-slate-300">
                            {idx + 1}
                          </span>
                          <div className="min-w-0 flex-1">
                            <span className="block font-semibold text-slate-800 dark:text-slate-100 break-words leading-tight">
                              {def?.label || colId}
                            </span>
                            <span className="block text-[10px] text-slate-400 dark:text-slate-500">
                              {def?.category || "General Information"}
                            </span>
                          </div>
                        </div>

                        {/* Small Neutral Reorder & Remove Controls */}
                        <div className="flex items-center gap-1 shrink-0">
                          <button
                            type="button"
                            disabled={isFirst}
                            onClick={() => moveColumn(idx, "UP")}
                            title="Move Left / Up (earlier)"
                            aria-label={`Move ${def?.label || colId} up`}
                            className="h-6 w-6 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-25 disabled:hover:bg-transparent flex items-center justify-center text-xs transition focus:outline-hidden focus:ring-1 focus:ring-blue-500"
                          >
                            ▲
                          </button>
                          <button
                            type="button"
                            disabled={isLast}
                            onClick={() => moveColumn(idx, "DOWN")}
                            title="Move Right / Down (later)"
                            aria-label={`Move ${def?.label || colId} down`}
                            className="h-6 w-6 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-700 disabled:opacity-25 disabled:hover:bg-transparent flex items-center justify-center text-xs transition focus:outline-hidden focus:ring-1 focus:ring-blue-500"
                          >
                            ▼
                          </button>
                          <button
                            type="button"
                            onClick={() => removeColumn(colId)}
                            title="Remove column"
                            aria-label={`Remove ${def?.label || colId}`}
                            className="h-6 w-6 rounded border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/40 flex items-center justify-center text-xs font-bold transition focus:outline-hidden focus:ring-1 focus:ring-red-500 ml-0.5"
                          >
                            ✕
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ================= STEP 3: APPLY FILTERS ================= */}
      {currentStep === 3 && (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-100 dark:border-slate-800">
            <div>
              <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
                Step 3: Apply Advanced Filters
              </h2>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                Target records using dynamic criteria with AND / OR conjunctions and nested groups.
              </p>
            </div>
            <button
              type="button"
              onClick={clearAllFilters}
              className="text-xs text-red-600 dark:text-red-400 hover:underline font-semibold"
            >
              Clear All Filters
            </button>
          </div>

          {/* Render Root Filter Group */}
          <div>{renderFilterGroup(filterGroup, [], 0)}</div>
        </div>
      )}

      {/* ================= STEP 4: GROUP & SORT ================= */}
      {currentStep === 4 && (
        <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-6">
          <div className="pb-3 border-b border-slate-100 dark:border-slate-800">
            <h2 className="text-base font-bold text-slate-900 dark:text-slate-100">
              Step 4: Configure Grouping & Sorting
            </h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Decide how records should be grouped and organized in the final output.
            </p>
          </div>

          <div className="grid gap-5 sm:grid-cols-3">
            {/* Primary Grouping */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Primary Grouping
              </label>
              <select
                value={groupBy}
                onChange={e => {
                  setGroupBy(e.target.value);
                  markCustomized();
                }}
                className="mt-1 w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2 text-xs text-slate-800 dark:text-slate-100 font-medium focus:ring-2 focus:ring-blue-500"
              >
                <option value="companyName">Group By Company Name (Recommended)</option>
                <option value="branch">Group By Branch</option>
                <option value="mentorName">Group By Faculty Mentor</option>
                <option value="internshipMode">Group By Internship Mode</option>
                <option value="companyType">Group By Company Type</option>
                <option value="internshipSource">Group By Internship Source</option>
                <option value="internshipStatus">Group By Internship Status</option>
                <option value="NONE">No Grouping (Flat Table)</option>
              </select>
              <p className="mt-1.5 text-[11px] text-slate-500 dark:text-slate-400">
                {groupBy === "companyName"
                  ? "Students with the same company will appear together."
                  : groupBy === "NONE"
                  ? "Records will be displayed in a single unified table."
                  : `Records with the same ${groupBy} will appear together.`}
              </p>
            </div>

            {/* Sort Field */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Sort Records By
              </label>
              <select
                value={sortBy}
                onChange={e => {
                  setSortBy(e.target.value);
                  markCustomized();
                }}
                className="mt-1 w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3.5 py-2 text-xs text-slate-800 dark:text-slate-100 font-medium focus:ring-2 focus:ring-blue-500"
              >
                <option value="companyName">Company Name</option>
                <option value="studentName">Student Name</option>
                <option value="uid">Student UID</option>
                <option value="annualCtc">Annual CTC (LPA)</option>
                <option value="stipend">Monthly Stipend</option>
                <option value="attendancePercentage">Attendance Percentage</option>
                <option value="startDate">Internship Start Date</option>
              </select>
              <p className="mt-1.5 text-[11px] text-slate-500 dark:text-slate-400">
                Ordering within each group.
              </p>
            </div>

            {/* Sort Direction */}
            <div>
              <label className="block text-xs font-bold text-slate-700 dark:text-slate-300">
                Sort Direction
              </label>
              <div className="mt-1 inline-flex w-full rounded-xl border border-slate-200 dark:border-slate-700 p-0.5 bg-white dark:bg-slate-900">
                <button
                  type="button"
                  onClick={() => {
                    setSortDirection("ASC");
                    markCustomized();
                  }}
                  className={`flex-1 rounded-lg py-1.5 text-xs font-semibold transition ${
                    sortDirection === "ASC"
                      ? "bg-blue-100 dark:bg-blue-900/60 text-blue-900 dark:text-blue-100 border border-blue-300 dark:border-blue-700 shadow-2xs"
                      : "text-slate-600 dark:text-slate-400 hover:text-slate-900"
                  }`}
                >
                  Ascending (A–Z / Low–High)
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setSortDirection("DESC");
                    markCustomized();
                  }}
                  className={`flex-1 rounded-lg py-1.5 text-xs font-semibold transition ${
                    sortDirection === "DESC"
                      ? "bg-blue-100 dark:bg-blue-900/60 text-blue-900 dark:text-blue-100 border border-blue-300 dark:border-blue-700 shadow-2xs"
                      : "text-slate-600 dark:text-slate-400 hover:text-slate-900"
                  }`}
                >
                  Descending (Z–A / High–Low)
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ================= STEP 5: PREVIEW, SAVE & EXPORT ================= */}
      {currentStep === 5 && (
        <div className="space-y-6">
          {/* Report Configuration Summary Card */}
          <div className="rounded-2xl border border-blue-200 dark:border-blue-900/60 bg-blue-50/50 dark:bg-blue-950/30 p-5 shadow-xs">
            <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-blue-200/60 dark:border-blue-900/40">
              <h3 className="text-xs font-bold uppercase tracking-wider text-blue-900 dark:text-blue-200">
                Report Configuration Summary
              </h3>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => goToStep(1)}
                  className="text-xs text-blue-700 dark:text-blue-300 font-semibold hover:underline"
                >
                  Edit Details
                </button>
                <span className="text-slate-300">·</span>
                <button
                  type="button"
                  onClick={() => goToStep(2)}
                  className="text-xs text-blue-700 dark:text-blue-300 font-semibold hover:underline"
                >
                  Edit Columns
                </button>
                <span className="text-slate-300">·</span>
                <button
                  type="button"
                  onClick={() => goToStep(3)}
                  className="text-xs text-blue-700 dark:text-blue-300 font-semibold hover:underline"
                >
                  Edit Filters
                </button>
              </div>
            </div>

            <div className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4 text-xs">
              <div>
                <span className="text-slate-500 block">Report Title:</span>
                <strong className="text-slate-900 dark:text-slate-100">{reportTitle}</strong>
              </div>
              <div>
                <span className="text-slate-500 block">Scope & Session:</span>
                <strong className="text-slate-900 dark:text-slate-100">
                  {scope === "STUDENT" ? "Student-wise" : "Internship-wise"} ·{" "}
                  {sessionId
                    ? sessions.find(s => s.id === sessionId)?.name || `Session #${sessionId}`
                    : "All Sessions"}
                </strong>
              </div>
              <div>
                <span className="text-slate-500 block">Grouping & Sorting:</span>
                <strong className="text-slate-900 dark:text-slate-100">
                  Group: {groupBy === "NONE" ? "None" : groupBy} · Sort: {sortBy} ({sortDirection})
                </strong>
              </div>
              <div>
                <span className="text-slate-500 block">Columns ({selectedColumns.length}):</span>
                <span className="text-slate-700 dark:text-slate-300 truncate block">
                  {selectedColumns.join(", ")}
                </span>
              </div>
            </div>

            <div className="mt-2.5 pt-2.5 border-t border-blue-200/50 dark:border-blue-900/40 text-xs text-slate-600 dark:text-slate-400">
              <span className="font-semibold text-slate-700 dark:text-slate-300">Active Filters: </span>
              <span>{formatGroupReadable(filterGroup) || "None (All records included)"}</span>
            </div>
          </div>

          {/* Export Error Banner in Step 5 */}
          {exportError && (
            <div className="rounded-xl bg-red-50 dark:bg-red-950/40 border border-red-200 dark:border-red-900/60 p-3.5 text-xs text-red-700 dark:text-red-300 flex items-center justify-between shadow-2xs">
              <div className="flex items-center gap-2">
                <span className="font-bold">Export Notice:</span>
                <span>{exportError}</span>
              </div>
              <button
                type="button"
                onClick={() => setExportError("")}
                className="text-red-600 dark:text-red-400 font-bold ml-3 hover:opacity-80 text-sm"
                aria-label="Dismiss export notice"
              >
                ×
              </button>
            </div>
          )}

          {/* Action Bar (Preview & Exports) - Hierarchy: Secondary Outlined / Neutral Controls */}
          <div className="flex flex-wrap items-center justify-between gap-3 p-4 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xs">
            <div className="flex flex-wrap items-center gap-2.5">
              <button
                type="button"
                disabled={previewLoading || loadingCatalog}
                onClick={() => fetchPreview(0)}
                className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 px-4 py-2 text-xs font-semibold text-slate-800 dark:text-slate-100 shadow-2xs transition disabled:opacity-50"
              >
                {previewLoading ? "Refreshing Preview…" : "Refresh Preview"}
              </button>

              <button
                type="button"
                disabled={Boolean(exportingFormat)}
                onClick={() => triggerExport("PDF")}
                aria-label="Export report as official PDF"
                className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2 text-xs font-semibold text-slate-800 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-2xs transition disabled:opacity-50"
              >
                {exportingFormat === "PDF" ? "Generating PDF…" : "Export Official PDF"}
              </button>

              <button
                type="button"
                disabled={Boolean(exportingFormat)}
                onClick={() => triggerExport("EXCEL")}
                aria-label="Export report as Excel spreadsheet"
                className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2 text-xs font-semibold text-slate-800 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-2xs transition disabled:opacity-50"
              >
                {exportingFormat === "EXCEL" ? "Generating Excel…" : "Export Excel (.xlsx)"}
              </button>

              <button
                type="button"
                disabled={Boolean(exportingFormat)}
                onClick={() => triggerExport("CSV")}
                aria-label="Export report as CSV file"
                className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2 text-xs font-semibold text-slate-800 dark:text-slate-100 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-2xs transition disabled:opacity-50"
              >
                {exportingFormat === "CSV" ? "Generating CSV…" : "Export CSV"}
              </button>
            </div>

            <div>
              <button
                type="button"
                onClick={() => setSaveModalOpen(true)}
                className="rounded-xl border border-blue-300 dark:border-blue-700 bg-white dark:bg-slate-800 px-4 py-2 text-xs font-semibold text-blue-700 dark:text-blue-300 hover:bg-blue-50 dark:hover:bg-blue-900/30 transition shadow-2xs"
              >
                Save Configuration
              </button>
            </div>
          </div>

          {/* Report Preview Display */}
          {previewLoading ? (
            <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-12 text-center text-xs text-slate-500">
              Generating report preview from database…
            </div>
          ) : previewData ? (
            <div className="rounded-2xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 p-6 shadow-xs space-y-6">
              {/* Institutional Header Display */}
              <div className="text-center pb-5 border-b-2 border-slate-200 dark:border-slate-700">
                <h1 className="text-lg font-black tracking-wide text-slate-900 dark:text-slate-100 uppercase">
                  {previewData.institutionName}
                </h1>
                <h2 className="text-sm font-semibold text-slate-600 dark:text-slate-400 uppercase mt-0.5">
                  {previewData.departmentName}
                </h2>
                <div className="my-2 inline-block px-3 py-1 bg-slate-100 dark:bg-slate-800 rounded-md text-xs font-bold tracking-widest text-slate-800 dark:text-slate-200 uppercase">
                  {previewData.headerTitle}
                </div>
                <h3 className="text-base font-bold text-blue-700 dark:text-blue-400 mt-1">
                  {previewData.reportTitle}
                </h3>
                <div className="mt-2 flex flex-wrap items-center justify-center gap-4 text-xs text-slate-500 dark:text-slate-400">
                  <span>
                    <strong>Academic Session:</strong> {previewData.academicSessionName}
                  </span>
                  <span>•</span>
                  <span>
                    <strong>Generated:</strong> {previewData.generatedAt} ({previewData.timezone})
                  </span>
                  <span>•</span>
                  <span>
                    <strong>Scope:</strong>{" "}
                    {previewData.scope === "STUDENT" ? "Student-wise" : "Internship-wise"}
                  </span>
                </div>
                <div className="mt-1 text-xs text-slate-500 italic">
                  <strong>Applied Filters:</strong> {previewData.filterSummary}
                </div>
              </div>

              {/* Metric Summary Cards */}
              <div className="grid gap-3 grid-cols-2 sm:grid-cols-3 lg:grid-cols-6">
                <div className="rounded-xl bg-slate-50 dark:bg-slate-800/80 p-3 border border-slate-100 dark:border-slate-700/60">
                  <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Total Students
                  </span>
                  <strong className="text-lg font-bold text-slate-900 dark:text-slate-100">
                    {previewData.totalStudents}
                  </strong>
                </div>

                <div className="rounded-xl bg-slate-50 dark:bg-slate-800/80 p-3 border border-slate-100 dark:border-slate-700/60">
                  <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Total Internships
                  </span>
                  <strong className="text-lg font-bold text-slate-900 dark:text-slate-100">
                    {previewData.totalInternships}
                  </strong>
                </div>

                <div className="rounded-xl bg-slate-50 dark:bg-slate-800/80 p-3 border border-slate-100 dark:border-slate-700/60">
                  <span className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider">
                    Total Records
                  </span>
                  <strong className="text-lg font-bold text-slate-900 dark:text-slate-100">
                    {previewData.totalRecords}
                  </strong>
                </div>

                {previewData.overallSummary["Average CTC (LPA)"] !== undefined && (
                  <div className="rounded-xl bg-blue-50/70 dark:bg-blue-950/40 p-3 border border-blue-100 dark:border-blue-900/60">
                    <span className="block text-[11px] font-semibold text-blue-700 dark:text-blue-300 uppercase tracking-wider">
                      Avg CTC (LPA)
                    </span>
                    <strong className="text-lg font-bold text-blue-900 dark:text-blue-100">
                      ₹{Number(previewData.overallSummary["Average CTC (LPA)"]).toFixed(2)} LPA
                    </strong>
                  </div>
                )}

                {previewData.overallSummary["Average Stipend"] !== undefined && (
                  <div className="rounded-xl bg-emerald-50/70 dark:bg-emerald-950/40 p-3 border border-emerald-100 dark:border-emerald-900/60">
                    <span className="block text-[11px] font-semibold text-emerald-700 dark:text-emerald-300 uppercase tracking-wider">
                      Avg Stipend
                    </span>
                    <strong className="text-lg font-bold text-emerald-900 dark:text-emerald-100">
                      ₹{Number(previewData.overallSummary["Average Stipend"]).toLocaleString("en-IN")}
                    </strong>
                  </div>
                )}

                {previewData.overallSummary["Average Attendance (%)"] !== undefined && (
                  <div className="rounded-xl bg-violet-50/70 dark:bg-violet-950/40 p-3 border border-violet-100 dark:border-violet-900/60">
                    <span className="block text-[11px] font-semibold text-violet-700 dark:text-violet-300 uppercase tracking-wider">
                      Avg Attendance
                    </span>
                    <strong className="text-lg font-bold text-violet-900 dark:text-violet-100">
                      {Number(previewData.overallSummary["Average Attendance (%)"]).toFixed(1)}%
                    </strong>
                  </div>
                )}
              </div>

              {/* In-Table Search & Pagination Header */}
              <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    placeholder="Search within preview results…"
                    value={tableSearch}
                    onChange={e => setTableSearch(e.target.value)}
                    onKeyDown={e => {
                      if (e.key === "Enter") fetchPreview(0);
                    }}
                    className="w-64 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-800 px-3 py-1.5 text-xs text-slate-800 dark:text-slate-100"
                  />
                  <button
                    type="button"
                    onClick={() => fetchPreview(0)}
                    className="rounded-lg bg-slate-200 dark:bg-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-300"
                  >
                    Filter
                  </button>
                </div>
                <span className="text-xs text-slate-500">
                  Showing page {previewData.page + 1} of {Math.max(1, previewData.totalPages)} (
                  {previewData.totalRecords} records)
                </span>
              </div>

              {/* Grouped Table Sections */}
              <div className="space-y-6">
                {previewData.groups.length === 0 ? (
                  <div className="py-12 text-center text-sm text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800/40 rounded-xl border border-dashed border-slate-200 dark:border-slate-700">
                    <p className="font-semibold text-slate-700 dark:text-slate-300">
                      No records match the selected filters.
                    </p>
                    <button
                      type="button"
                      onClick={() => goToStep(3)}
                      className="mt-3 inline-flex items-center gap-1 rounded-lg bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 px-3 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-50 transition"
                    >
                      ← Edit Filters
                    </button>
                  </div>
                ) : (
                  previewData.groups.map(group => (
                    <div
                      key={group.groupValue}
                      className="rounded-xl border border-slate-200 dark:border-slate-700 overflow-hidden shadow-2xs"
                    >
                      {/* Group Banner */}
                      {previewData.groupBy !== "NONE" && (
                        <div className="bg-slate-100 dark:bg-slate-800 px-4 py-2.5 flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 dark:border-slate-700">
                          <div className="flex items-center gap-2">
                            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">
                              {catalog.find(f => f.id === previewData.groupBy)?.label || "Group"}:
                            </span>
                            <span className="text-sm font-black text-slate-900 dark:text-slate-100">
                              {group.groupValue}
                            </span>
                            <span className="rounded-full bg-slate-200 dark:bg-slate-700 px-2 py-0.5 text-[11px] font-semibold text-slate-700 dark:text-slate-300 ml-1">
                              {group.recordCount} {group.recordCount === 1 ? "record" : "records"}
                            </span>
                          </div>

                          {/* Group Summary Metrics */}
                          <div className="flex items-center gap-3 text-xs text-slate-600 dark:text-slate-300">
                            {group.summary["Average CTC (LPA)"] !== undefined && (
                              <span>
                                Avg CTC:{" "}
                                <strong>
                                  ₹{Number(group.summary["Average CTC (LPA)"]).toFixed(2)} LPA
                                </strong>
                              </span>
                            )}
                            {group.summary["Average Stipend"] !== undefined && (
                              <span>
                                Avg Stipend:{" "}
                                <strong>
                                  ₹{Number(group.summary["Average Stipend"]).toLocaleString("en-IN")}
                                </strong>
                              </span>
                            )}
                            {group.summary["Average Attendance (%)"] !== undefined && (
                              <span>
                                Avg Att:{" "}
                                <strong>
                                  {Number(group.summary["Average Attendance (%)"]).toFixed(1)}%
                                </strong>
                              </span>
                            )}
                          </div>
                        </div>
                      )}

                      {/* Group Rows Table */}
                      <div className="overflow-x-auto">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-slate-50/80 dark:bg-slate-900/60 border-b border-slate-200 dark:border-slate-700 text-slate-600 dark:text-slate-300">
                            <tr>
                              <th className="p-3 w-10 text-center font-bold text-slate-400">#</th>
                              {previewData.columns.map(col => (
                                <th key={col.id} className="whitespace-nowrap p-3 font-bold">
                                  {col.label}
                                </th>
                              ))}
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
                            {group.rows.map((row, rIdx) => (
                              <tr
                                key={`row-${rIdx}`}
                                className="hover:bg-blue-50/30 dark:hover:bg-slate-800/40 transition-colors"
                              >
                                <td className="p-3 text-center text-slate-400">{rIdx + 1}</td>
                                {previewData.columns.map(col => {
                                  const val = row[col.id];
                                  let displayVal = "—";
                                  if (val != null) {
                                    if (typeof val === "boolean") {
                                      displayVal = val ? "Yes" : "No";
                                    } else if (col.id === "annualCtc" && typeof val === "number") {
                                      displayVal = `₹${val.toFixed(2)} LPA`;
                                    } else if (col.id === "stipend" && typeof val === "number") {
                                      displayVal = `₹${Number(val).toLocaleString("en-IN")}`;
                                    } else if (
                                      col.id === "attendancePercentage" &&
                                      typeof val === "number"
                                    ) {
                                      displayVal = `${val.toFixed(1)}%`;
                                    } else {
                                      displayVal = String(val);
                                    }
                                  }
                                  return (
                                    <td
                                      key={col.id}
                                      className="whitespace-nowrap p-3 text-slate-800 dark:text-slate-200"
                                    >
                                      {displayVal}
                                    </td>
                                  );
                                })}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    </div>
                  ))
                )}
              </div>

              {/* Pagination Controls */}
              {previewData.totalPages > 1 && (
                <div className="flex items-center justify-between border-t border-slate-100 dark:border-slate-800 pt-4">
                  <button
                    type="button"
                    disabled={previewPage === 0 || previewLoading}
                    onClick={() => fetchPreview(previewPage - 1)}
                    className="rounded-xl border border-slate-200 dark:border-slate-700 px-3.5 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-50 disabled:opacity-40"
                  >
                    ◀ Previous 50
                  </button>
                  <span className="text-xs text-slate-500">
                    Page {previewPage + 1} of {previewData.totalPages}
                  </span>
                  <button
                    type="button"
                    disabled={previewPage + 1 >= previewData.totalPages || previewLoading}
                    onClick={() => fetchPreview(previewPage + 1)}
                    className="rounded-xl border border-slate-200 dark:border-slate-700 px-3.5 py-1.5 text-xs font-semibold text-slate-700 dark:text-slate-300 hover:bg-slate-50 disabled:opacity-40"
                  >
                    Next 50 ▶
                  </button>
                </div>
              )}
            </div>
          ) : null}
        </div>
      )}

      {/* ================= STEPPER FOOTER (NEXT / BACK) - Primary Action ================= */}
      <div className="flex items-center justify-between p-4 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-xs">
        <div>
          {currentStep > 1 && (
            <button
              type="button"
              onClick={handleBack}
              className="rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 px-4 py-2 text-xs font-semibold text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-2xs transition"
            >
              ← Back: Step {currentStep - 1}
            </button>
          )}
        </div>

        <div className="text-xs text-slate-500 font-medium">
          Step {currentStep} of 5: {STEPS[currentStep - 1].title}
        </div>

        <div>
          {currentStep < 5 ? (
            <button
              type="button"
              onClick={handleNext}
              className="rounded-xl bg-blue-600 hover:bg-blue-700 px-5 py-2 text-xs font-bold text-white shadow-sm transition"
            >
              Next: Step {currentStep + 1} →
            </button>
          ) : (
            <button
              type="button"
              disabled={previewLoading}
              onClick={() => fetchPreview(0)}
              className="rounded-xl bg-blue-600 hover:bg-blue-700 px-5 py-2 text-xs font-bold text-white shadow-sm transition"
            >
              {previewLoading ? "Refreshing…" : "Generate & Preview Report"}
            </button>
          )}
        </div>
      </div>

      {/* Save Configuration Modal */}
      {saveModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-xs p-4">
          <div className="w-full max-w-md rounded-2xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 p-6 shadow-xl">
            <h3 className="text-base font-bold text-slate-900 dark:text-slate-100">
              Save Report Configuration
            </h3>
            <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
              Store this report&apos;s layout, selected columns, and filter rules in the database to re-run at any time.
            </p>

            <div className="mt-4 space-y-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Configuration Title <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={reportTitle}
                  onChange={e => setReportTitle(e.target.value)}
                  className="mt-1 w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2 text-xs text-slate-800 dark:text-slate-100"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 dark:text-slate-300">
                  Description / Purpose (Optional)
                </label>
                <textarea
                  rows={2}
                  value={reportDescription}
                  onChange={e => setReportDescription(e.target.value)}
                  placeholder="e.g. Generated monthly for HOD reporting…"
                  className="mt-1 w-full rounded-xl border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 px-3.5 py-2 text-xs text-slate-800 dark:text-slate-100"
                />
              </div>

              <div className="text-[11px] text-slate-400">
                <span>
                  Scope: <strong>{scope}</strong> · Columns: <strong>{selectedColumns.length}</strong>
                </span>
              </div>
            </div>

            <div className="mt-5 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setSaveModalOpen(false)}
                className="rounded-xl border border-slate-200 dark:border-slate-700 px-4 py-2 text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={savingConfig || !reportTitle.trim()}
                onClick={saveConfiguration}
                className="rounded-xl bg-blue-600 hover:bg-blue-700 px-4 py-2 text-xs font-bold text-white transition disabled:opacity-50"
              >
                {savingConfig ? "Saving…" : "Save Definition"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
