(ns rubbercoord.governor
  "RubberCoordGovernor — the independent safety/scope layer gating
  every plant scheduling/logistics proposal an advisor may make for a
  rubber products machine operator crew. The governor never dispatches
  hardware itself, never operates molding/extrusion/curing equipment
  itself, and never finalizes a molding-operation-execution decision
  (e.g. deciding to proceed with a specific molding, extrusion or
  curing run) or a plant-safety-clearance decision (e.g. declaring a
  plant or curing area safety cleared), and never overrides a plant
  safety officer's judgment — those are permanently out of this
  actor's scope and remain a plant safety officer's exclusive judgment
  (README's 'Robotics premise': this actor coordinates PLANT
  SCHEDULING/LOGISTICS ONLY — it never operates molding, extrusion or
  curing equipment itself). Modeled closely on cloud-itonami-isco-8122's
  platingcoord.governor.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. operator provenance    — the crew member must be independently
                                verified/registered before any action.
    2. plant provenance      — the rubber products molding/extrusion/
                                curing plant or line must be
                                independently verified/registered
                                before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never operates molding, extrusion or
                                curing equipment itself; it only gates
                                what the advisor may coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-crew-operation,
                                :flag-safety-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                molding-operation-execution decision
                                (e.g. deciding to proceed with a
                                specific molding, extrusion or curing
                                run), or a plant-safety-clearance
                                decision (e.g. declaring a plant or
                                curing area safety cleared), or to
                                override a plant safety officer's
                                judgment, is a hard, permanent block
                                (checked both against the proposed :op
                                and, defense-in-depth, against the
                                proposal's :rationale text — matched as
                                full finalization/execution ACTION
                                phrases such as \"finalize the molding
                                operation\" / \"declare the plant
                                safety cleared\" / \"override the plant
                                safety officer's judgment\", never as
                                bare nouns like \"rubber\", \"vulcanize\"
                                or \"mold\", so the check can never
                                self-trip on the advisor's own routine
                                rationale text, e.g. \"logged work
                                record for operator …\" or \"scheduled
                                crew operation for molding task …\" or
                                \"…routed for plant safety officer
                                review\" — all three legitimately
                                contain bare domain nouns but none is a
                                finalization action, and all are
                                exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-safety-concern (a crush/entanglement, heat-exposure,
                                fume-exposure or equipment-condition
                                concern always escalates to a human,
                                never auto-commits).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`).

  This actor coordinates plant scheduling/logistics ONLY — it never
  operates molding, extrusion or curing equipment itself, and it never
  makes a plant-safety-clearance decision itself; those decisions
  always route to a human plant safety officer, either via a hard
  permanent block on the op-allowlist (rules 4/5 above) or via a
  mandatory escalation (rule 6 above)."
  (:require [kotoba.lang.text :as str]
            [rubbercoord.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 2000)

(def allowed-ops
  #{:log-work-record :schedule-crew-operation
    :flag-safety-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-molding-decision :finalize-molding-operation
    :authorize-molding-run
    :proceed-with-molding-run
    :finalize-plant-safety-clearance
    :declare-plant-safety-cleared
    :declare-plant-safe-for-operation
    :clear-plant-for-operation
    :override-plant-safety-officer-judgment
    :override-safety-officer-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("rubber", "vulcanize", "mold", "molding", "extrusion", "curing",
;; "press", "roller", "plant", "safety", "officer") — so this can
;; never match inside the mock advisor's own default rationale text
;; (which legitimately contains those bare nouns, e.g. "molding task"
;; / "plant safety officer review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the molding run" "proceed with the curing run" "proceed with the extrusion run"
   "authorize the molding run" "authorize the curing run" "authorize the extrusion run"
   "finalize the molding decision" "finalize the molding operation"
   "finalize the curing operation" "finalize the extrusion operation"
   "declare the plant safety cleared" "declare the plant safe for operation"
   "finalize the plant safety clearance" "finalize the plant-safety clearance"
   "clear the plant for operation"
   "override the plant safety officer's judgment"
   "override the safety officer's judgment"
   "override plant safety officer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal operator-record plant-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? operator-record)
      (conj {:rule :no-operator
             :detail "未登録 operator への提案は不可（operator record は独立して検証・登録済みでなければならない）"})

      (nil? plant-record)
      (conj {:rule :no-plant
             :detail "未登録 plant への提案は不可（plant record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は成形・押出・加硫作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "成形作業実行判断・plant safety クリアランス判断の確定、および plant safety officer の判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `rubbercoord.store/Store`. Pure — never
  mutates the store, never dispatches a molding, extrusion or curing
  operation."
  [request _context proposal store]
  (let [operator-record (store/operator store (:operator-id request))
        plant-record (some->> (:plant-id proposal) (store/plant store))
        hard (hard-violations proposal operator-record plant-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-safety-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
