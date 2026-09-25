package orgmgr

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"gopkg.in/yaml.v3"
)

// Template is a parsed organization template (doc 15 §4.1).
type Template struct {
	Name          string          `yaml:"name"`
	Description   string          `yaml:"description"`
	Departments   []TemplateDept  `yaml:"departments"`
	DNA           map[string]float64 `yaml:"-"`
	Constitution  map[string]interface{} `yaml:"-"`
	DNAPath       string          `yaml:"dna_ref"`
	ConstitutionPath string       `yaml:"constitution_ref"`
}

// TemplateDept is one department in the template.
type TemplateDept struct {
	ID       string            `yaml:"id"`
	Name     string            `yaml:"name"`
	Charter  map[string]interface{} `yaml:"charter"`
	Roles    []TemplateRole    `yaml:"roles"`
	Head     string            `yaml:"head"`
}

// TemplateRole is a role slot.
type TemplateRole struct {
	Role  string `yaml:"role"`
	Count int    `yaml:"count"`
}

// LoadTemplate reads an organization template directory
// (templates/organizations/<name>/).
func LoadTemplate(name string, baseDir string) (*Template, error) {
	dir := filepath.Join(baseDir, name)
	orgFile := filepath.Join(dir, "org.yaml")
	data, err := os.ReadFile(orgFile)
	if err != nil {
		return nil, fmt.Errorf("read %s: %w", orgFile, err)
	}
	var t Template
	if err := yaml.Unmarshal(data, &t); err != nil {
		return nil, fmt.Errorf("parse %s: %w", orgFile, err)
	}
	t.Name = name

	// DNA
	dnaPath := t.DNAPath
	if dnaPath == "" {
		dnaPath = "dna.yaml"
	}
	dnaFile := filepath.Join(dir, dnaPath)
	if dnaData, err := os.ReadFile(dnaFile); err == nil {
		var dna map[string]float64
		if err := yaml.Unmarshal(dnaData, &dna); err != nil {
			return nil, fmt.Errorf("parse dna: %w", err)
		}
		t.DNA = dna
	} else {
		t.DNA = defaultDNA()
	}

	// Constitution
	constPath := t.ConstitutionPath
	if constPath == "" {
		constPath = "constitution.md"
	}
	constFile := filepath.Join(dir, constPath)
	if constData, err := os.ReadFile(constFile); err == nil {
		t.Constitution = parseConstitution(string(constData))
	} else {
		t.Constitution = defaultConstitution()
	}
	return &t, nil
}

// defaultDNA supplies the startup defaults when dna.yaml is absent.
func defaultDNA() map[string]float64 {
	return map[string]float64{
		"innovation": 0.70, "risk": 0.60, "hierarchy": 0.50,
		"accuracy": 0.80, "speed": 0.60, "transparency": 0.75,
		"creativity": 0.65, "long_term_thinking": 0.60, "security": 0.55,
	}
}

// parseConstitution converts the markdown constitution into the JSONB shape
// (doc 13 §13.4.2).
func parseConstitution(md string) map[string]interface{} {
	out := map[string]interface{}{
		"mission":     extractSection(md, "## Mission"),
		"principles":  extractList(md, "## Principles"),
		"values":      extractList(md, "## Core Values"),
		"ethics":      extractList(md, "## Ethics"),
		"decision_rules": extractDecisionRules(md),
	}
	return out
}

func defaultConstitution() map[string]interface{} {
	return map[string]interface{}{
		"mission":    "Investigate and decide on startup viability.",
		"principles": []string{"Evidence before opinion", "Dissent is recorded"},
		"values":     []string{"accuracy", "transparency", "pragmatism"},
		"ethics":     []string{"no fabrication", "cite sources", "flag uncertainty"},
		"decision_rules": map[string]interface{}{
			"voting_quorum": 0.6, "trust_weighted": true,
			"amendment_threshold": 0.75, "ceo_veto": false,
			"confidence_threshold": 0.72, "max_cycles": 3,
		},
	}
}

func extractSection(md, heading string) string {
	lines := strings.Split(md, "\n")
	inSection := false
	var out []string
	for _, l := range lines {
		if strings.HasPrefix(l, heading) {
			inSection = true
			continue
		}
		if inSection {
			if strings.HasPrefix(l, "##") {
				break
			}
			trimmed := strings.TrimSpace(l)
			if trimmed != "" {
				out = append(out, trimmed)
			}
		}
	}
	return strings.Join(out, " ")
}

func extractList(md, heading string) []string {
	lines := strings.Split(md, "\n")
	inSection := false
	var out []string
	for _, l := range lines {
		if strings.HasPrefix(l, heading) {
			inSection = true
			continue
		}
		if inSection {
			if strings.HasPrefix(l, "##") {
				break
			}
			trimmed := strings.TrimSpace(l)
			trimmed = strings.TrimPrefix(trimmed, "- ")
			trimmed = strings.TrimPrefix(trimmed, "* ")
			if trimmed != "" {
				out = append(out, trimmed)
			}
		}
	}
	return out
}

func extractDecisionRules(md string) map[string]interface{} {
	rules := map[string]interface{}{}
	lines := strings.Split(md, "\n")
	inSection := false
	for _, l := range lines {
		if strings.HasPrefix(l, "## Decision Rules") {
			inSection = true
			continue
		}
		if inSection {
			if strings.HasPrefix(l, "##") {
				break
			}
			trimmed := strings.TrimSpace(l)
			trimmed = strings.TrimPrefix(trimmed, "- ")
			if idx := strings.Index(trimmed, ":"); idx > 0 {
				key := strings.TrimSpace(trimmed[:idx])
				val := strings.TrimSpace(trimmed[idx+1:])
				switch key {
				case "voting_quorum", "amendment_threshold", "confidence_threshold":
					var f float64
					if _, err := fmt.Sscanf(val, "%g", &f); err == nil {
						rules[key] = f
					}
				case "trust_weighted", "ceo_veto":
					rules[key] = val == "true"
				case "max_cycles":
					var n int
					if _, err := fmt.Sscanf(val, "%d", &n); err == nil {
						rules[key] = n
					}
				default:
					rules[key] = val
				}
			}
		}
	}
	return rules
}
