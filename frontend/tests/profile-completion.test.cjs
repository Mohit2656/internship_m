const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs"), path = require("node:path"), ts = require("typescript");

function loadProfileCompletion() {
  const source = fs.readFileSync(path.join(__dirname, "../lib/profile-completion.ts"), "utf8");
  const module = { exports: {} };
  new Function("require", "module", "exports", ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 }
  }).outputText)(name => require(name), module, module.exports);
  return module.exports;
}

const { calculateProfileCompletion } = loadProfileCompletion();

test("profile completion is 0% when student profile and professional profile are empty", () => {
  const result = calculateProfileCompletion({
    profile: null,
    professionalProfile: null,
  });
  assert.equal(result.percentage, 0);
  assert.equal(result.remainingPercentage, 100);
  assert.equal(result.isComplete, false);
  assert.ok(result.nextAction);
  assert.equal(result.nextAction.href, "/student/settings?tab=profile&edit=true");
});

test("does not penalize student for internship details when not yet applicable (pre-commencement)", () => {
  const completePersonal = {
    personalEmail: "student@example.com",
    phoneNumber: "9876543210",
    bloodGroup: "O+",
    rollNumber: "42",
    academicYear: "2026-2027",
    semester: 8,
    section: "A",
    permanentAddress: "123 Main St",
    guardianName: "Parent",
    guardianContact: "9876543211",
    emergencyContact: "9876543212",
    maskedAadhaar: "XXXX-XXXX-1234",
  };
  const completeProfessional = {
    headline: "Full Stack Developer",
    careerInterests: "Cloud and Web Engineering",
    resume: { id: 1, originalFilename: "resume.pdf" },
    skillsUpdatedAt: "2026-11-01T10:00:00Z",
  };

  const result = calculateProfileCompletion({
    profile: completePersonal,
    professionalProfile: completeProfessional,
    skillsCount: 3,
    onboarding: { workflowStatus: "PRE_COMMENCEMENT" },
    details: null,
  });

  assert.equal(result.percentage, 100);
  assert.equal(result.remainingPercentage, 0);
  assert.equal(result.isComplete, true);
  assert.equal(result.nextAction, null);
  const internshipSection = result.sections.find(s => s.id === "internship");
  assert.equal(internshipSection.applicable, false);
});

test("calculates accurate percentage and lists missing fields when partially complete", () => {
  const partialPersonal = {
    personalEmail: "student@example.com",
    phoneNumber: "9876543210",
    bloodGroup: "O+",
    // missing rollNumber, academicYear, semester, address, etc.
  };

  const result = calculateProfileCompletion({
    profile: partialPersonal,
    professionalProfile: null,
    onboarding: { workflowStatus: "PRE_COMMENCEMENT" },
  });

  assert.ok(result.percentage > 0 && result.percentage < 100);
  assert.equal(result.remainingPercentage, 100 - result.percentage);
  assert.equal(result.isComplete, false);
  const personalSection = result.sections.find(s => s.id === "personal");
  assert.ok(personalSection.missingFields.includes("Roll Number"));
  assert.ok(personalSection.missingFields.includes("Emergency Contact"));
  assert.ok(personalSection.missingFields.includes("Section A or B"));
});

test("an otherwise complete profile still requires the student to choose an assigned section", () => {
  const profile = { personalEmail: "student@example.com", phoneNumber: "9876543210", bloodGroup: "O+", rollNumber: "42", academicYear: "2026-2027", semester: 8, permanentAddress: "Address", guardianName: "Guardian", guardianContact: "9876543211", emergencyContact: "9876543212", maskedAadhaar: "XXXX XXXX 1234" };
  const professionalProfile = { headline: "Developer", careerInterests: "Engineering", resume: { id: 1 }, skillsUpdatedAt: "2026-11-02T10:00:00Z" };
  const incomplete = calculateProfileCompletion({ profile, professionalProfile });
  assert.equal(incomplete.isComplete, false);
  assert.ok(incomplete.percentage < 100);
  assert.deepEqual(incomplete.sections[0].missingFields, ["Section A or B"]);
  assert.equal(incomplete.nextAction.href, "/student/settings?tab=profile&edit=true");
  assert.equal(calculateProfileCompletion({ profile: { ...profile, section: "B" }, professionalProfile }).isComplete, true);
});
