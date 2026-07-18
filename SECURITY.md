# Security Policy

This project handles rubber products machine operator operating
workflows. Treat vulnerabilities as potentially high impact even when
the demo data is synthetic — this domain's failure modes include real
heavy-machinery hazard (crush/entanglement risk from molding presses
and rollers), heat exposure from vulcanization/curing ovens, and fume
exposure from rubber-compound off-gassing, alongside physical
worker-safety risk.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real operator, plant or business data exposure
- authorization bypass
- Rubber Products Plant Scheduling Coordination Governor bypass
- audit-ledger tampering
- over-disclosure in reports or exports
- unsafe robot action dispatch
- any path that lets a proposal reach a molding-operation-execution
  decision, a plant-safety-clearance decision, or a
  plant-safety-officer-override decision

## Reporting

Use GitHub private vulnerability reporting when available for the repository.
If that is unavailable, contact the repository maintainers through the
cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on operator/plant data, policy enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real operator/plant/business data outside this repository.
- Run policy tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
