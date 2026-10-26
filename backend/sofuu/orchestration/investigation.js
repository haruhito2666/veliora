import { newId, nowISO, sleep } from "../core/ids.js";
import {
  RESEARCH_DEPARTMENTS,
  STARTUP_TEMPLATE,
  deliverablesFor,
} from "../core/templates.js";

function terminal(state) {
  return state === "completed" || state === "cancelled" || state === "failed" || state === "archived";
}

function firstWorker(org, departmentName) {
  const department = org.departments.find((item) => item.name === departmentName);
  if (!department) return null;
  const worker = org.workers.find(
    (item) => item.department_id === department.department_id && item.state === "active",
  );
  if (!worker) return null;
  return { department, worker };
}

function challengerFor(org, evidenceDepartment) {
  for (const department of org.departments) {
    if (department.name === evidenceDepartment) continue;
    const worker = org.workers.find(
      (item) => item.department_id === department.department_id && item.state === "active",
    );
    if (worker) return { department, worker };
  }
  return null;
}

function uniqueSpeakers(evidence) {
  const seen = new Set();
  const speakers = [];
  for (const item of evidence) {
    if (seen.has(item.department)) continue;
    seen.add(item.department);
    speakers.push({
      worker_id: item.submitted_by,
      department_id: item.department_id,
      department: item.department,
      role: item.role,
      claim: item.claim,
    });
  }
  return speakers;
}

function sortByTime(items, field) {
  return items.slice().sort((a, b) => String(a[field] || "").localeCompare(String(b[field] || "")));
}

export class InvestigationOrchestrator {
  constructor({ store, bus, llm, config }) {
    this.store = store;
    this.bus = bus;
    this.llm = llm;
    this.config = config;
  }

  // ── reads ────────────────────────────────────────────────────────────

  getRecord(invID) {
    return this.store.get("investigations", invID);
  }

  tasks(invID) {
    return sortByTime(this.store.list("tasks", { ref: invID }), "created_at");
  }

  evidence(invID) {
    return sortByTime(this.store.list("evidence", { ref: invID }), "submitted_at");
  }

  challenges(invID) {
    return sortByTime(this.store.list("challenges", { ref: invID }), "resolved_at");
  }

  arguments(invID) {
    return sortByTime(this.store.list("arguments", { ref: invID }), "at");
  }

  votes(invID) {
    return this.store
      .list("votes", { ref: invID })
      .sort((a, b) => (b.cycle - a.cycle) || String(a.created_at).localeCompare(String(b.created_at)));
  }

  meetings(orgID) {
    return sortByTime(this.store.list("meetings", { ref: orgID }), "called_at");
  }

  reportRecord(invID) {
    return this.store.get("reports", invID);
  }

  dto(inv) {
    if (!inv) return null;
    const cycleTasks = this.tasks(inv.investigation_id).filter(
      (task) => task.task_type === "collect_evidence" && task.cycle === inv.cycle_count,
    );
    const planTasks = cycleTasks.length
      ? cycleTasks.map((task) => ({
          task_id: task.task_id,
          department: task.department,
          description: task.description,
          deliverables: task.deliverables,
          status: task.status,
        }))
      : (inv.plan && inv.plan.tasks) || [];
    return {
      investigation_id: inv.investigation_id,
      organization_id: inv.organization_id,
      title: inv.title,
      prompt: inv.prompt,
      state: inv.state,
      current_phase: inv.current_phase,
      charter: inv.charter,
      plan: { tasks: planTasks },
      cycle_count: inv.cycle_count,
      confidence: inv.confidence || 0,
      created_at: inv.created_at,
      completed_at: inv.completed_at || null,
    };
  }

  get(orgID, invID) {
    const inv = this.getRecord(invID);
    if (!inv || inv.organization_id !== orgID) return null;
    return this.dto(inv);
  }

  list(orgID) {
    return this.store
      .list("investigations", { ref: orgID })
      .sort((a, b) => String(b.created_at).localeCompare(String(a.created_at)))
      .map((inv) => this.dto(inv));
  }

  getDebate(orgID, invID) {
    const debates = this.store.list("debates", { ref: invID });
    if (!debates.length) return null;
    // Store.list returns newest-first.
    const debate = debates[0];
    const transcript = this.arguments(invID)
      .filter((argument) => argument.debate_id === debate.debate_id)
      .map((argument) => ({
        argument_id: argument.argument_id,
        worker_id: argument.worker_id,
        role: argument.role,
        department: argument.department,
        round: argument.round,
        stance: argument.stance,
        text: argument.text,
        at: argument.at,
      }));
    const participants = [];
    for (const item of transcript) {
      if (!participants.includes(item.worker_id)) participants.push(item.worker_id);
    }
    return {
      debate_id: debate.debate_id,
      organization_id: orgID,
      investigation_id: invID,
      meeting_id: debate.meeting_id,
      topic: debate.topic,
      debate_type: debate.debate_type,
      state: debate.state,
      phase: debate.phase,
      participants,
      transcript,
      opened_at: debate.opened_at,
      concluded_at: debate.concluded_at || null,
    };
  }

  getMeeting(orgID, meetingID) {
    return this.store.get("meetings", meetingID);
  }

  getReport(orgID, invID) {
    const inv = this.getRecord(invID);
    if (!inv || inv.organization_id !== orgID) return { error: "not_found" };
    if (inv.state !== "completed" && inv.state !== "archived") return { error: "not_complete" };
    const report = this.reportRecord(invID);
    if (!report) return { error: "not_complete" };
    return { report };
  }

  // ── mutations ────────────────────────────────────────────────────────

  create(org, prompt, depth = "standard") {
    if (!org) throw new Error("organization not found");
    if (!prompt || !prompt.trim()) throw new Error("prompt required");

    const invID = newId("inv");
    const title = prompt.length > 60 ? `${prompt.slice(0, 60)}…` : prompt;
    const inv = {
      investigation_id: invID,
      organization_id: org.organization_id,
      title,
      prompt,
      state: "planning",
      current_phase: "scoping",
      charter: {
        goal: prompt,
        departments: STARTUP_TEMPLATE.departments.map((department) => department.name),
        success_criteria: ["Evidence survives peer challenge", "Dissent is preserved"],
        confidence_threshold: this.config.confidenceThreshold,
        priority: "normal",
        depth,
      },
      plan: { tasks: [] },
      cycle_count: 1,
      confidence: 0,
      final_recommendation: null,
      created_at: nowISO(),
      completed_at: null,
    };
    this.store.put("investigations", invID, inv, org.organization_id);

    this.bus.publish(org.organization_id, "investigation.started", {
      investigation_id: invID,
      title,
      prompt,
      depth,
    });
    this.bus.publish(org.organization_id, "investigation.charter_signed", {
      investigation_id: invID,
      charter: inv.charter,
    });

    const tasks = this._createResearchTasks(org, inv, 1);
    inv.plan.tasks = tasks.map((task) => ({
      task_id: task.task_id,
      department: task.department,
      description: task.description,
      deliverables: task.deliverables,
      status: task.status,
    }));
    this.store.put("investigations", invID, inv, org.organization_id);
    this._transition(inv, "researching", "research");

    Promise.resolve()
      .then(() => this.run(invID))
      .catch((error) => this._fail(invID, error));

    return this.dto(this.getRecord(invID));
  }

  cancel(orgID, invID) {
    const inv = this.getRecord(invID);
    if (!inv || inv.organization_id !== orgID) return false;
    if (terminal(inv.state)) return false;
    inv.state = "cancelled";
    inv.current_phase = "cancelled";
    this.store.put("investigations", invID, inv, orgID);
    this.bus.publish(orgID, "investigation.cancelled", { investigation_id: invID });
    return true;
  }

  // First-pass recovery: resume investigations that were interrupted during
  // research. Later phases have their own idempotency work to do before they
  // can be resumed safely, so they are left visible rather than silently
  // restarted into a duplicate debate.
  resumeActive() {
    for (const inv of this.store.list("investigations", {})) {
      if (terminal(inv.state)) continue;
      if (inv.state === "planning") {
        this._transition(inv, "researching", "research");
      }
      if (inv.state === "researching" || inv.state === "planning") {
        Promise.resolve()
          .then(() => this.run(inv.investigation_id))
          .catch((error) => this._fail(inv.investigation_id, error));
      } else {
        console.warn(
          `not resuming ${inv.investigation_id} from state ${inv.state} in this pass`,
        );
      }
    }
  }

  async run(invID) {
    let inv = this.getRecord(invID);
    if (!inv) return;

    while (!terminal(inv.state)) {
      const cycle = inv.cycle_count;
      await this._research(inv, cycle);
      inv = this.getRecord(invID);
      if (!inv || terminal(inv.state)) return;

      await this._challenge(inv, cycle);
      inv = this.getRecord(invID);
      if (!inv || terminal(inv.state)) return;

      await this._debate(inv, cycle);
      inv = this.getRecord(invID);
      if (!inv || terminal(inv.state)) return;

      await this._synthesis(inv, cycle);
      inv = this.getRecord(invID);
      if (!inv || terminal(inv.state)) return;

      await this._vote(inv, cycle);
      inv = this.getRecord(invID);
      if (!inv || terminal(inv.state)) return;

      if (inv.state === "researching") continue;
      if (inv.state === "reporting") {
        await this._report(inv);
        return;
      }
      return;
    }
  }

  // ── pipeline phases ──────────────────────────────────────────────────

  async _research(inv, cycle) {
    const org = this.store.get("orgs", inv.organization_id);
    if (!org) throw new Error("organization disappeared during research");

    const tasks = this.tasks(inv.investigation_id).filter(
      (task) => task.task_type === "collect_evidence" && task.cycle === cycle,
    );

    for (const task of tasks) {
      if (task.status === "done") continue;
      task.status = "in_progress";
      task.started_at = nowISO();
      this.store.put("tasks", task.task_id, task, inv.investigation_id);
      this.bus.publish(inv.organization_id, "task.started", {
        task_id: task.task_id,
        investigation_id: inv.investigation_id,
        task_type: task.task_type,
      });

      const department = STARTUP_TEMPLATE.departments.find(
        (item) => item.name === task.department,
      );
      const result = await this.llm.evidence({
        org,
        inv,
        department,
        role: task.role,
        task,
        cycle,
      });
      await sleep(this.config.stepDelayMs);

      const claim = String(result.claim || result.summary || "Evidence collected.").trim();
      const summary = String(result.summary || claim).trim();
      const confidence = Math.max(0, Math.min(1, Number(result.confidence) || 0.5));
      const evidenceID = newId("evd");
      const evidence = {
        evidence_id: evidenceID,
        investigation_id: inv.investigation_id,
        task_id: task.task_id,
        submitted_by: task.worker_id,
        department_id: task.department_id,
        department: task.department,
        role: task.role,
        source_type: result.source_type || "internal_analysis",
        summary,
        claim,
        confidence,
        status: "submitted",
        challenge_count: 0,
        sources: result.sources || [],
        cycle,
        submitted_at: nowISO(),
      };
      this.store.put("evidence", evidenceID, evidence, inv.investigation_id);

      task.status = "done";
      task.completed_at = nowISO();
      this.store.put("tasks", task.task_id, task, inv.investigation_id);

      this.bus.publish(inv.organization_id, "evidence.submitted", {
        investigation_id: inv.investigation_id,
        evidence_id: evidenceID,
        claim,
        confidence,
        department: task.department,
      });
      this.bus.publish(inv.organization_id, "task.completed", {
        task_id: task.task_id,
        investigation_id: inv.investigation_id,
        task_type: task.task_type,
      });
    }

    this._transition(inv, "analyzing", "analysis");
  }

  async _challenge(inv, cycle) {
    const org = this.store.get("orgs", inv.organization_id);
    const cycleEvidence = this.evidence(inv.investigation_id).filter(
      (item) => item.cycle === cycle,
    );
    if (!cycleEvidence.length) {
      throw new Error("research completed without evidence to challenge");
    }
    const evidence = cycleEvidence.filter((item) => item.status === "submitted");
    if (!evidence.length) {
      // A restart after the last challenge was accepted should move on, not
      // re-open the analysis phase.
      this._transition(inv, "debating", "debate");
      return;
    }

    for (const item of evidence) {
      const challenger = challengerFor(org, item.department);
      if (!challenger) throw new Error(`no independent challenger for ${item.department}`);

      const verdict = await this.llm.challenge({
        org,
        inv,
        department: challenger.department,
        role: challenger.worker.role,
        evidence: item,
        cycle,
      });
      await sleep(this.config.stepDelayMs);

      const text = verdict && verdict.challenge ? String(verdict.challenge).trim() : "";
      item.status = "accepted";
      if (text) item.challenge_count = (item.challenge_count || 0) + 1;
      this.store.put("evidence", item.evidence_id, item, inv.investigation_id);

      if (text) {
        const challenge = {
          challenge_id: newId("chl"),
          investigation_id: inv.investigation_id,
          evidence_id: item.evidence_id,
          department: item.department,
          challenged_by_worker_id: challenger.worker.worker_id,
          challenge_text: text,
          status: "resolved_qualified",
          cycle,
          resolved_at: nowISO(),
        };
        this.store.put("challenges", challenge.challenge_id, challenge, inv.investigation_id);
        this.bus.publish(inv.organization_id, "evidence.challenged", {
          investigation_id: inv.investigation_id,
          evidence_id: item.evidence_id,
          challenge_id: challenge.challenge_id,
          challenge_text: text,
        });
      }
      this.bus.publish(inv.organization_id, "evidence.upheld", {
        investigation_id: inv.investigation_id,
        evidence_id: item.evidence_id,
        qualified: Boolean(text),
      });
    }

    this._transition(inv, "debating", "debate");
  }

  async _debate(inv, cycle) {
    const org = this.store.get("orgs", inv.organization_id);
    const accepted = this.evidence(inv.investigation_id).filter(
      (item) => item.cycle === cycle && item.status === "accepted",
    );
    const speakers = uniqueSpeakers(accepted);
    if (speakers.length < 2) throw new Error("debate needs at least two departments with accepted evidence");

    const debateID = newId("dbt");
    const meetingID = newId("mtg");
    const participants = speakers.map((speaker) => speaker.worker_id);
    const debate = {
      debate_id: debateID,
      investigation_id: inv.investigation_id,
      organization_id: inv.organization_id,
      meeting_id: meetingID,
      topic: `Should the organization proceed with: ${inv.title}`,
      debate_type: "strategic",
      state: "open",
      phase: "opening",
      participants,
      cycle,
      opened_at: nowISO(),
      concluded_at: null,
    };
    const meeting = {
      meeting_id: meetingID,
      organization_id: inv.organization_id,
      investigation_id: inv.investigation_id,
      title: `Investigation debate: ${inv.title}`,
      state: "conducting",
      agenda: [
        { id: "positions", title: "Cross-department evidence positions", type: "debate_ref", time_budget_min: 15 },
        { id: "rebuttal", title: "Rebuttal round", type: "decision", time_budget_min: 15 },
      ],
      participants,
      chair: "",
      called_at: nowISO(),
      started_at: nowISO(),
      ended_at: null,
    };
    this.store.put("debates", debateID, debate, inv.investigation_id);
    this.store.put("meetings", meetingID, meeting, inv.organization_id);
    this.bus.publish(inv.organization_id, "debate.opened", {
      debate_id: debateID,
      investigation_id: inv.investigation_id,
      topic: debate.topic,
      participants,
    });
    this.bus.publish(inv.organization_id, "meeting.called", {
      meeting_id: meetingID,
      investigation_id: inv.investigation_id,
      title: meeting.title,
      debate_id: debateID,
      participants,
    });
    this.bus.publish(inv.organization_id, "meeting.started", {
      meeting_id: meetingID,
      debate_id: debateID,
      quorum_met: true,
    });

    for (let round = 1; round <= 2; round += 1) {
      const priorTurns = this._transcriptText(debateID, inv.investigation_id);
      for (const speaker of speakers) {
        const department = STARTUP_TEMPLATE.departments.find(
          (item) => item.name === speaker.department,
        );
        const turn = await this.llm.debate({
          org,
          inv,
          department,
          role: speaker.role,
          round,
          stance: round === 1 ? "support" : "oppose",
          otherTurns: priorTurns,
          cycle,
        });
        await sleep(this.config.stepDelayMs);
        const text = String((turn && (turn.text || turn.response)) || "").trim();
        if (!text) throw new Error("debate worker returned an empty turn");
        const stance = ["support", "oppose", "qualified"].includes(turn.stance)
          ? turn.stance
          : round === 1 ? "support" : "oppose";
        const argument = {
          argument_id: newId("arg"),
          debate_id: debateID,
          investigation_id: inv.investigation_id,
          worker_id: speaker.worker_id,
          department: speaker.department,
          role: speaker.role,
          round,
          stance,
          text,
          cycle,
          at: nowISO(),
        };
        this.store.put("arguments", argument.argument_id, argument, inv.investigation_id);
        this.bus.publish(
          inv.organization_id,
          stance === "support" ? "debate.position" : round > 1 ? "debate.rebuttal" : "debate.challenged",
          {
            debate_id: debateID,
            investigation_id: inv.investigation_id,
            worker_id: speaker.worker_id,
            department: speaker.department,
            stance,
            round,
            text,
          },
        );
      }
    }

    debate.state = "concluded";
    debate.phase = "closed";
    debate.concluded_at = nowISO();
    this.store.put("debates", debateID, debate, inv.investigation_id);
    meeting.state = "ended";
    meeting.ended_at = nowISO();
    this.store.put("meetings", meetingID, meeting, inv.organization_id);
    this.bus.publish(inv.organization_id, "debate.concluded", {
      debate_id: debateID,
      investigation_id: inv.investigation_id,
      verdict: "converged",
      rounds: 2,
    });
    this.bus.publish(inv.organization_id, "meeting.concluded", {
      meeting_id: meetingID,
      investigation_id: inv.investigation_id,
      outcome: "transcript frozen for synthesis",
    });

    this._transition(inv, "synthesizing", "synthesis");
  }

  async _synthesis(inv, cycle) {
    const org = this.store.get("orgs", inv.organization_id);
    const ceo = firstWorker(org, "CEO Office");
    if (!ceo) throw new Error("no CEO worker available for synthesis");

    const accepted = this.evidence(inv.investigation_id).filter(
      (item) => item.cycle === cycle && item.status === "accepted",
    );
    const dissent = this._dissent(inv.investigation_id, cycle);
    const transcript = this._transcriptText(
      this._latestDebateID(inv.investigation_id),
      inv.investigation_id,
    );
    const result = await this.llm.synthesize({
      org,
      inv,
      department: ceo.department,
      role: ceo.worker.role,
      evidence: accepted,
      dissent,
      transcript,
      cycle,
    });
    await sleep(this.config.stepDelayMs);

    const recommendation = String(result.recommendation || "").trim();
    if (!recommendation) throw new Error("synthesis returned no recommendation");
    inv.final_recommendation = {
      ...result,
      recommendation,
      confidence: Math.max(0, Math.min(1, Number(result.confidence) || 0.72)),
    };
    this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
    this.bus.publish(inv.organization_id, "debate.synthesis", {
      investigation_id: inv.investigation_id,
      recommendation,
      confidence: inv.final_recommendation.confidence,
    });

    this._transition(inv, "vote", "synthesis");
  }

  async _vote(inv, cycle) {
    const org = this.store.get("orgs", inv.organization_id);
    const debateID = this._latestDebateID(inv.investigation_id);
    const args = this.arguments(inv.investigation_id).filter(
      (argument) => argument.debate_id === debateID,
    );
    const speakers = [];
    const seen = new Set();
    for (const argument of args) {
      if (seen.has(argument.worker_id)) continue;
      seen.add(argument.worker_id);
      speakers.push(argument);
    }
    if (!speakers.length) throw new Error("no debate speakers available to vote");

    const recommendation = this._recommendationText(inv);
    for (const speaker of speakers) {
      const worker = org.workers.find((item) => item.worker_id === speaker.worker_id);
      const result = await this.llm.vote({
        org,
        inv,
        department: STARTUP_TEMPLATE.departments.find((item) => item.name === speaker.department),
        role: speaker.role,
        recommendation,
        position: speaker.text,
        cycle,
      });
      await sleep(this.config.stepDelayMs);
      let choice = String(result.choice || "").toLowerCase().trim();
      if (!["yes", "no", "abstain"].includes(choice)) choice = "abstain";
      const vote = {
        vote_id: newId("vot"),
        investigation_id: inv.investigation_id,
        voter_worker_id: speaker.worker_id,
        department: speaker.department,
        choice,
        weight: worker ? worker.trust_score : 0.5,
        reasoning: String(result.reasoning || ""),
        cycle,
        created_at: nowISO(),
      };
      this.store.put("votes", vote.vote_id, vote, inv.investigation_id);
      this.bus.publish(inv.organization_id, "debate.vote", {
        investigation_id: inv.investigation_id,
        vote_id: vote.vote_id,
        choice,
        weight: vote.weight,
        department: vote.department,
      });
    }

    const tally = { yes: 0, no: 0, abstain: 0 };
    for (const vote of this.votes(inv.investigation_id).filter((item) => item.cycle === cycle)) {
      tally[vote.choice] = (tally[vote.choice] || 0) + vote.weight;
    }
    const decided = tally.yes + tally.no;
    const confidence = decided > 0 ? tally.yes / decided : 0;
    const verdict = decided === 0 ? "no_consensus" : tally.yes >= tally.no ? "yes" : "no";

    inv.confidence = confidence;
    inv.state = "confidence_chk";
    inv.current_phase = "synthesis";
    inv.final_recommendation = {
      ...(inv.final_recommendation || {}),
      vote_verdict: verdict,
      vote_tally: tally,
    };
    this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
    this.bus.publish(inv.organization_id, "investigation.confidence", {
      investigation_id: inv.investigation_id,
      confidence,
      threshold: this.config.confidenceThreshold,
      cycle,
    });

    if (confidence < this.config.confidenceThreshold && cycle < this.config.maxResearchCycles) {
      this._concludeDebate(inv, debateID);
      const next = cycle + 1;
      const tasks = this._createResearchTasks(org, inv, next);
      inv.cycle_count = next;
      inv.plan.tasks = tasks.map((task) => ({
        task_id: task.task_id,
        department: task.department,
        description: task.description,
        deliverables: task.deliverables,
        status: task.status,
      }));
      this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
      this._transition(inv, "researching", "research");
      return;
    }

    this._transition(inv, "reporting", "report");
  }

  async _report(inv) {
    const org = this.store.get("orgs", inv.organization_id);
    const ceo = firstWorker(org, "CEO Office");
    const accepted = this.evidence(inv.investigation_id).filter(
      (item) => item.status === "accepted",
    );
    const debateID = this._latestDebateID(inv.investigation_id);
    const transcript = this._transcriptText(debateID, inv.investigation_id);
    const dissent = this._dissent(inv.investigation_id, inv.cycle_count);
    const result = await this.llm.report({
      org,
      inv,
      department: ceo ? ceo.department : null,
      role: ceo ? ceo.worker.role : "CEO",
      recommendation: this._recommendationText(inv),
      evidence: accepted,
      dissent,
      transcript,
      confidence: inv.confidence,
      cycle: inv.cycle_count,
    });
    await sleep(this.config.stepDelayMs);

    const report = {
      report_id: `rep_${inv.investigation_id.slice(-8)}`,
      investigation_id: inv.investigation_id,
      organization_id: inv.organization_id,
      executive_summary: String(result.executive_summary || result.summary || ""),
      recommendation: String(result.recommendation || ""),
      confidence: inv.confidence || Number(result.confidence) || 0,
      dissenting_opinions: result.dissenting_opinions || result.dissent || dissent,
      supporting_evidence: result.supporting_evidence || accepted.map((item) => item.summary),
      risks: result.risks || [],
      opportunities: result.opportunities || [],
      next_steps: result.next_steps || [],
      full_discussion: result.full_discussion_summary || result.full_discussion || transcript,
      created_at: nowISO(),
    };
    if (!report.recommendation) throw new Error("board report returned no recommendation");
    this.store.put("reports", inv.investigation_id, report, inv.organization_id);

    if (ceo) {
      const taskID = newId("tsk");
      const task = {
        task_id: taskID,
        investigation_id: inv.investigation_id,
        department_id: ceo.department.department_id,
        department: ceo.department.name,
        worker_id: ceo.worker.worker_id,
        role: ceo.worker.role,
        task_type: "board_report",
        description: "Generate final board report",
        deliverables: "board report",
        status: "done",
        cycle: inv.cycle_count,
        created_at: nowISO(),
        completed_at: nowISO(),
      };
      this.store.put("tasks", taskID, task, inv.investigation_id);
      this.bus.publish(inv.organization_id, "task.completed", {
        task_id: taskID,
        investigation_id: inv.investigation_id,
        task_type: "board_report",
      });
    }

    inv.state = "completed";
    inv.current_phase = "report";
    inv.completed_at = nowISO();
    this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
    this.bus.publish(inv.organization_id, "investigation.completed", {
      investigation_id: inv.investigation_id,
      report_id: report.report_id,
    });
    this.bus.publish(inv.organization_id, "investigation.report_ready", {
      investigation_id: inv.investigation_id,
      report_id: report.report_id,
      formats: ["json"],
    });
  }

  // ── helpers ──────────────────────────────────────────────────────────

  _createResearchTasks(org, inv, cycle) {
    const tasks = [];
    for (const departmentTemplate of RESEARCH_DEPARTMENTS) {
      const found = firstWorker(org, departmentTemplate.name);
      if (!found) throw new Error(`department ${departmentTemplate.name} has no active worker`);
      const task = {
        task_id: newId("tsk"),
        investigation_id: inv.investigation_id,
        department_id: found.department.department_id,
        department: found.department.name,
        worker_id: found.worker.worker_id,
        role: found.worker.role,
        task_type: "collect_evidence",
        description: `Cycle ${cycle} research for ${found.department.name}: gather evidence for "${inv.prompt}"`,
        deliverables: deliverablesFor(found.department.name),
        status: "pending",
        cycle,
        created_at: nowISO(),
      };
      this.store.put("tasks", task.task_id, task, inv.investigation_id);
      this.bus.publish(inv.organization_id, "task.assigned", {
        task_id: task.task_id,
        investigation_id: inv.investigation_id,
        task_type: task.task_type,
        department: task.department,
        cycle,
      });
      tasks.push(task);
    }
    return tasks;
  }

  _transition(inv, state, phase) {
    if (inv.state === state && inv.current_phase === phase) return;
    const from = inv.state;
    inv.state = state;
    inv.current_phase = phase;
    this.store.put("investigations", inv.investigation_id, inv, inv.organization_id);
    this.bus.publish(inv.organization_id, "investigation.phase_changed", {
      investigation_id: inv.investigation_id,
      from_phase: from,
      to_phase: state,
      state,
      phase,
    });
  }

  _concludeDebate(inv, debateID) {
    const debate = this.store.get("debates", debateID);
    if (debate) {
      debate.state = "concluded";
      debate.phase = "closed";
      debate.concluded_at = nowISO();
      this.store.put("debates", debateID, debate, inv.investigation_id);
    }
    for (const meeting of this.store.list("meetings", { ref: inv.organization_id })) {
      if (meeting.investigation_id === inv.investigation_id && meeting.state !== "ended") {
        meeting.state = "ended";
        meeting.ended_at = nowISO();
        this.store.put("meetings", meeting.meeting_id, meeting, inv.organization_id);
      }
    }
  }

  _fail(invID, error) {
    const inv = this.getRecord(invID);
    if (!inv || terminal(inv.state)) return;
    inv.state = "failed";
    inv.current_phase = "failed";
    inv.error = error && error.message ? error.message : String(error);
    this.store.put("investigations", invID, inv, inv.organization_id);
    this.bus.publish(inv.organization_id, "investigation.failed", {
      investigation_id: invID,
      error: inv.error,
    });
    console.error(`investigation ${invID} failed: ${inv.error}`);
  }

  _latestDebateID(invID) {
    const debates = this.store.list("debates", { ref: invID });
    return debates.length ? debates[0].debate_id : "";
  }

  _transcriptText(debateID, invID) {
    if (!debateID || !invID) return "";
    return this.arguments(invID)
      .filter((argument) => argument.debate_id === debateID)
      .map((argument) => `${argument.role} (${argument.department}, ${argument.stance}): ${argument.text}`)
      .join(" | ");
  }

  _dissent(invID, cycle) {
    const items = [];
    for (const argument of this.arguments(invID)) {
      if (argument.cycle !== cycle) continue;
      if (argument.stance === "oppose" || argument.stance === "qualified") {
        items.push(`${argument.department}: ${argument.text}`);
      }
    }
    for (const challenge of this.challenges(invID)) {
      if (challenge.cycle !== cycle) continue;
      items.push(`qualified evidence (${challenge.department}): ${challenge.challenge_text}`);
    }
    return items;
  }

  _recommendationText(inv) {
    const rec = inv.final_recommendation || {};
    const parts = [];
    if (rec.recommendation) parts.push(`Recommendation: ${rec.recommendation}`);
    if (rec.executive_summary) parts.push(`Executive summary: ${rec.executive_summary}`);
    if (Array.isArray(rec.dissent) && rec.dissent.length) {
      parts.push(`Dissent: ${rec.dissent.join("; ")}`);
    }
    return parts.join("\n");
  }
}
