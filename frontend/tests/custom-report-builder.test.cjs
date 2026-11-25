const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path'), ts = require('typescript'), React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const nodes = tree => !tree || typeof tree !== 'object' ? [] : [tree, ...React.Children.toArray(tree.props?.children).flatMap(nodes)];

test('CustomReportBuilder renders institutional header, columns, filters and grouping options', () => {
  const source = fs.readFileSync(path.join(__dirname, '../components/CustomReportBuilder.tsx'), 'utf8');
  assert.ok(source.includes('previewData.institutionName'), 'must render dynamic institution name in preview');
  assert.ok(source.includes('previewData.departmentName'), 'must render dynamic department name');
  assert.ok(source.includes('previewData.headerTitle'), 'must render standard header title');
  assert.ok(source.includes('Report Scope (Dual Internships)'), 'must include dual internship scope selector');
  assert.ok(source.includes('Group By Company Name'), 'must include company grouping option');
  assert.ok(source.includes('Save Configuration'), 'must include save configuration action');
  assert.ok(source.includes('Export Official PDF'), 'must include PDF export');
  assert.ok(source.includes('Export Excel (.xlsx)'), 'must include Excel export');
  assert.ok(source.includes('Export CSV'), 'must include CSV export');
  // 5-Step Workflow
  assert.ok(source.includes('Step 1: Configure Report Details'), 'must have Step 1 header');
  assert.ok(source.includes('Step 2: Select & Order Report Columns'), 'must have Step 2 header');
  assert.ok(source.includes('Step 3: Apply Advanced Filters'), 'must have Step 3 header');
  assert.ok(source.includes('Step 4: Configure Grouping & Sorting'), 'must have Step 4 header');
  assert.ok(source.includes('Report Configuration Summary'), 'must have Step 5 configuration summary card');
  // AND/OR button logic and beginner explanations
  assert.ok(source.includes('Include records only when every condition matches.'), 'must explain AND conjunction');
  assert.ok(source.includes('Include records when at least one condition matches.'), 'must explain OR conjunction');
  assert.ok(source.includes('bg-blue-100'), 'must use light-blue background for active conjunction');
  assert.ok(source.includes('text-blue-900'), 'must use dark-blue text for active conjunction');
  // Live Count
  assert.ok(source.includes('/admin/reports/custom/count'), 'must call custom count endpoint for debounced live count');
  assert.ok(source.includes('Matching current filters'), 'must render matching record count banner');
  // Column Selection and Reordering
  assert.ok(source.includes('Choose which fields appear in the report and arrange their order.'), 'must have Step 2 instruction');
  assert.ok(source.includes('Top to bottom = left to right in the exported report.'), 'must have selected columns ordering explanation');
  assert.ok(source.includes('Available Fields'), 'must have Available Fields area');
  assert.ok(source.includes('Selected Columns'), 'must have Selected Columns area');
  assert.ok(source.includes('disabled={isFirst}'), 'must disable Move Up on first column');
  assert.ok(source.includes('disabled={isLast}'), 'must disable Move Down on last column');
  assert.ok(source.includes('columns selected'), 'must have selected columns counter');
  // Presets and Reset
  assert.ok(source.includes('Quick Report Presets'), 'must offer quick presets');
  assert.ok(source.includes('Reset Builder'), 'must offer reset builder button');
  assert.ok(source.includes('Clear All Filters'), 'must offer clear filters button');
  assert.ok(source.includes('(Customized)'), 'must show customized status when preset is altered');
  // Emoji removal verification
  assert.ok(!source.includes('⚡'), 'no lightning emoji in builder');
  assert.ok(!source.includes('🔍'), 'no magnifying glass emoji in builder');
  assert.ok(!source.includes('📄'), 'no document emoji in builder');
  assert.ok(!source.includes('📊'), 'no chart emoji in builder');
  assert.ok(!source.includes('💾'), 'no floppy disk emoji in builder');
  assert.ok(!source.includes('⏳'), 'no hourglass emoji in builder');
});

test('SavedReportsList displays saved configurations and load in builder action', () => {
  const source = fs.readFileSync(path.join(__dirname, '../components/SavedReportsList.tsx'), 'utf8');
  assert.ok(source.includes('Saved Report Configurations'), 'must render saved report title');
  assert.ok(source.includes('Load in Builder'), 'must have action to load in builder');
  assert.ok(source.includes('/admin/reports/saved'), 'must call saved reports endpoint');
});

test('ReportsCenter contains compact internal segmented tabs for Quick Reports and Custom Report Builder', () => {
  const source = fs.readFileSync(path.join(__dirname, '../components/ReportsCenter.tsx'), 'utf8');
  assert.ok(source.includes('Quick Reports'), 'must have Quick Reports tab');
  assert.ok(source.includes('Custom Report Builder'), 'must have Custom Report Builder tab');
  assert.ok(source.includes('<CustomReportBuilder'), 'must render CustomReportBuilder component');
  assert.ok(source.includes('<SavedReportsList'), 'must render SavedReportsList component');
  assert.ok(!source.includes('⚡'), 'no emoji in Quick Reports tab');
  assert.ok(!source.includes('🛠️'), 'no emoji in Custom Report Builder tab');
  assert.ok(source.includes('border-b-2 border-blue-600'), 'active tab has clear blue underline');
  assert.ok(source.includes('role="tab"'), 'tabs have accessible role');
});

test('resolveExportFilename correctly extracts and sanitizes filenames and extensions across all formats', () => {
  const source = fs.readFileSync(path.join(__dirname, '../components/CustomReportBuilder.tsx'), 'utf8');
  const transpiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React } }).outputText;
  const mod = { exports: {} };
  const fn = new Function('module', 'exports', 'require', transpiled);
  fn(mod, mod.exports, (id) => {
    if (id === 'react') return React;
    if (id === 'axios') return {};
    if (id === '@/lib/api') return { api: {} };
    if (id === '@/lib/academic-sessions') return { useAcademicSessions: () => ({ sessions: [] }) };
    if (id === './SavedReportsList') return {};
    return {};
  });

  const { resolveExportFilename, parseFilenameFromContentDisposition } = mod.exports;
  assert.equal(typeof resolveExportFilename, 'function', 'resolveExportFilename must be exported');
  assert.equal(typeof parseFilenameFromContentDisposition, 'function', 'parseFilenameFromContentDisposition must be exported');

  // 1. Standard Content-Disposition
  assert.equal(
    resolveExportFilename('attachment; filename="Internship_Report.pdf"', 'Internship Report', 'PDF'),
    'Internship_Report.pdf'
  );
  assert.equal(
    resolveExportFilename('attachment; filename="Internship_Report.xlsx"', 'Internship Report', 'EXCEL'),
    'Internship_Report.xlsx'
  );
  assert.equal(
    resolveExportFilename('attachment; filename="Internship_Report.csv"', 'Internship Report', 'CSV'),
    'Internship_Report.csv'
  );

  // 2. RFC 5987 Unicode filename* parameter takes precedence over legacy filename
  assert.equal(
    resolveExportFilename(
      'attachment; filename="legacy.xlsx"; filename*=UTF-8\'\'Company_Wise_Internship_Report.xlsx',
      'Report',
      'EXCEL'
    ),
    'Company_Wise_Internship_Report.xlsx'
  );

  // 3. Spring Framework RFC-2047 Q-encoded fallback
  assert.equal(
    resolveExportFilename(
      'attachment; filename="=?UTF-8?Q?IMS=5FInternship=5FReport=5F2026-11-06.pdf?="; filename*=UTF-8\'\'IMS_Internship_Report_2026-11-06.pdf',
      'Report',
      'PDF'
    ),
    'IMS_Internship_Report_2026-11-06.pdf'
  );

  // 4. Filename containing spaces in header is sanitized cleanly
  assert.equal(
    resolveExportFilename('attachment; filename="Company Wise Internship Report.pdf"', 'Default', 'PDF'),
    'Company_Wise_Internship_Report.pdf'
  );

  // 5. Fallback from report title with spaces
  assert.equal(
    resolveExportFilename(undefined, 'Company Wise Internship Report', 'PDF'),
    'Company_Wise_Internship_Report.pdf'
  );

  // 6. Title already ending in .pdf does not duplicate .pdf
  assert.equal(
    resolveExportFilename(undefined, 'Internship_Report.pdf', 'PDF'),
    'Internship_Report.pdf'
  );
  assert.equal(
    resolveExportFilename(undefined, 'Internship Report.xlsx', 'EXCEL'),
    'Internship_Report.xlsx'
  );

  // 7. Corrupted trailing characters (such as .pdf=, .pdf_, .xlsx=) are cleaned
  assert.equal(
    resolveExportFilename('attachment; filename="Internship_Report.pdf_"', 'Fallback', 'PDF'),
    'Internship_Report.pdf'
  );
  assert.equal(
    resolveExportFilename('attachment; filename="Internship_Report.pdf=123"', 'Fallback', 'PDF'),
    'Internship_Report.pdf'
  );

  // 8. Special characters in report title are sanitized to safe filename characters
  assert.equal(
    resolveExportFilename(undefined, 'Student Report: 2025/2026 (Final!)', 'CSV'),
    'Student_Report_2025_2026_Final.csv'
  );

  // 9. Empty or whitespace report title falls back to safe default
  assert.equal(
    resolveExportFilename(undefined, '   ', 'PDF'),
    'Internship_Report.pdf'
  );
});
