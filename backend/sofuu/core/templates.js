export const STARTUP_TEMPLATE = {
  id: "startup",
  name: "Startup Society",
  description: "Decides whether to build a venture — market, feasibility, viability, and compliance.",
  departments: [
    {
      id: "ceo_office",
      name: "CEO Office",
      role: "CEO",
      charter: "Coordinate the investigation and synthesize the board recommendation.",
      roles: [{ role: "CEO", count: 1 }],
    },
    {
      id: "research",
      name: "Research Division",
      role: "RESEARCHER",
      charter: "Establish empirical truth about the market, competitors, and evidence base.",
      roles: [{ role: "RESEARCHER", count: 2 }],
    },
    {
      id: "engineering",
      name: "Engineering",
      role: "ENGINEER",
      charter: "Assess technical feasibility, build effort, and architectural risk.",
      roles: [{ role: "ENGINEER", count: 2 }],
    },
    {
      id: "finance",
      name: "Finance",
      role: "FINANCE_ANALYST",
      charter: "Model costs, revenue, runway, and return; keep the org solvent.",
      roles: [{ role: "FINANCE_ANALYST", count: 1 }],
    },
    {
      id: "marketing",
      name: "Marketing",
      role: "MARKETER",
      charter: "Establish demand, willingness to pay, and go-to-market viability.",
      roles: [{ role: "MARKETER", count: 1 }],
    },
    {
      id: "legal",
      name: "Legal",
      role: "LEGAL_ADVISOR",
      charter: "Keep the organization compliant; flag regulatory and legal risk.",
      roles: [{ role: "LEGAL_ADVISOR", count: 1 }],
    },
  ],
  dna: {
    innovation: 0.70,
    risk: 0.60,
    hierarchy: 0.50,
    accuracy: 0.80,
    speed: 0.60,
    transparency: 0.75,
    creativity: 0.65,
    long_term_thinking: 0.60,
    security: 0.55,
  },
  constitution: {
    mission: "Investigate and decide on startup viability with evidence, dissent, and a board-level recommendation.",
    principles: [
      "Evidence before opinion.",
      "Dissent is recorded, never erased.",
      "Every claim survives peer challenge before it shapes a recommendation.",
    ],
    values: ["accuracy", "transparency", "pragmatism"],
    ethics: ["No fabrication of evidence.", "Cite sources.", "Flag uncertainty explicitly."],
    decision_rules: {
      voting_quorum: 0.6,
      trust_weighted: true,
      amendment_threshold: 0.75,
      ceo_veto: false,
      confidence_threshold: 0.72,
      max_cycles: 3,
    },
  },
};

// The five departments that collect evidence; the CEO synthesizes and reports.
export const RESEARCH_DEPARTMENTS = STARTUP_TEMPLATE.departments.filter(
  (department) => department.id !== "ceo_office",
);

export function departmentById(id) {
  return STARTUP_TEMPLATE.departments.find((department) => department.id === id) || null;
}

export function departmentByName(name) {
  return STARTUP_TEMPLATE.departments.find((department) => department.name === name) || null;
}

export function deliverablesFor(departmentName) {
  switch (departmentName) {
    case "Research Division":
      return "market size + competitor brief with sources";
    case "Engineering":
      return "feasibility verdict + effort estimate";
    case "Finance":
      return "24-month cost/revenue model";
    case "Marketing":
      return "demand signal + willingness to pay";
    case "Legal":
      return "compliance risk register";
    default:
      return "brief";
  }
}

export function taskVerb(departmentName) {
  switch (departmentName) {
    case "Research Division":
      return "market and competitor";
    case "Engineering":
      return "technical feasibility";
    case "Finance":
      return "financial viability";
    case "Marketing":
      return "demand";
    case "Legal":
      return "regulatory and compliance";
    default:
      return "organizational";
  }
}
