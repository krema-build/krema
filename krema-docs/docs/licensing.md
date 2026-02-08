Here's how the main options stack up for your situation:

  Open Core (MIT/Apache core + proprietary features)

  How it works: The base Crema runtime is open source, Pro/Enterprise/Industrial features are closed.

  Problem for you: This is the highest-risk option for a solo dev. A company can fork the open core, build their own premium features on top, and outpace you with a 50-person team. This is exactly the scenario
  that burned Elastic, Redis Labs, and others — and they had full companies behind them, not a single developer. You'd be giving away the foundation and hoping your premium layer stays ahead. That's a hard race
   to win alone.

  BSL (Business Source License)

  How it works: Source is available and readable by anyone. Users can use, modify, and self-host freely. But they cannot offer a competing commercial product based on your code. After a set period (typically
  3-4 years), the code automatically converts to a true open-source license (Apache 2.0 or MIT).

  Why it fits you well:
  - Direct protection against the exact threat you're worried about — the "no competing product" clause blocks strip-mining
  - Users can still read, audit, patch, and self-host — so trust and transparency are maintained
  - The delayed open-source conversion signals good faith and builds community willingness to invest in the ecosystem
  - You maintain a single codebase, no need to split open vs. proprietary
  - Proven model: MariaDB (created BSL), CockroachDB, HashiCorp, Sentry all use it or something similar

  Downside: Open-source purists won't consider it open source (it isn't, by OSI definition). Some developers and organizations have policies against non-OSI licenses.

  FSL (Functional Source License)

  How it works: Similar to BSL but simpler and newer. Created by Sentry. Source is available, users can do anything except compete with you. Converts to Apache 2.0 or MIT after 2 years (shorter than typical
  BSL).

  Why it might be better than BSL: Shorter conversion window (2 years vs 3-4) makes it more palatable to the community. The terms are also clearer and less ambiguous than BSL's "Additional Use Grant" which
  varies per project.

  Downside: Newer, less legal precedent. Fewer people know what it is.

  AGPL + Commercial Dual License

  How it works: Code is AGPL (strong copyleft). Anyone can use it, but if they distribute or offer it as a service, they must open-source their entire stack. Companies that don't want AGPL obligations buy a
  commercial license from you.

  Why it's interesting for a framework: AGPL is particularly effective for frameworks because apps built with an AGPL framework arguably become derivative works — so most commercial users would need to buy the
  commercial license. Qt uses a similar model (LGPL + commercial) very successfully.

  Downside: AGPL is polarizing. Some companies have blanket bans on AGPL dependencies. It could limit adoption, which for a new framework is a real risk. Also, the "derivative work" argument for frameworks is
  legally gray — there's no definitive case law establishing whether an app built with an AGPL framework must itself be AGPL.

  What I'd consider in your position

  You have two distinct product surfaces with different dynamics:

  The SDK (what developers build apps with) — needs adoption, needs trust, needs people willing to commit their projects to your framework. Here, source availability matters a lot. Developers want to read the
  code, understand what it does, debug issues, and know they're not locked into a black box.

  The Platform (hosted services: sync, builds, analytics, crash reporting) — inherently protected by being a service. The code is secondary to the infrastructure. Even if someone had the code, replicating the
  operational infrastructure is a significant barrier.

  Given that split, BSL or FSL for the SDK and proprietary for the Platform is probably the most practical combination. The SDK code is visible and usable, protected against commercial copying, and eventually
  becomes fully open source. The Platform doesn't need source availability at all — its value is in the hosted service, not the code.

  The main tradeoff you're making with BSL/FSL vs. AGPL dual-license is adoption ceiling vs. monetization clarity. BSL/FSL is easier for companies to accept (they can use it freely, they just can't compete with
   you). AGPL dual-license generates more license revenue (companies must pay to avoid copyleft) but scares more people away.

  For a solo developer building a new framework that needs to gain traction, the BSL/FSL path is probably the safer bet — you want to minimize friction for adoption while keeping the one protection that
  actually matters to you.

✻ Baked for 1m 3s

❯ ok, are there famous examples that could fit my situation

⏺ Here are the most relevant cases, roughly ordered from closest to your situation to largest scale.

  Sidekiq — Mike Perham (solo developer)
  Background job processor for Ruby. Perham built and maintained it alone for years. The core is LGPL, with Sidekiq Pro and Sidekiq Enterprise as paid proprietary tiers adding batching, rate limiting, and
  reliability features. He's talked publicly about making millions per year as a solo developer with this model. This is open core done right by a single person — but it works partly because the core is an
  infrastructure library with a very clear "you need more reliability" upgrade path. A framework is harder to split cleanly.

  Plausible Analytics — small team (2-3 people initially)
  Privacy-focused web analytics, competing against Google Analytics. Licensed under AGPL. The AGPL ensures that anyone self-hosting can't strip it down and offer it as a competing SaaS without open-sourcing
  everything. Their revenue comes from the hosted service. This is the AGPL + hosted service model. Relevant to your Platform layer — the AGPL protects the code while the hosted service is the actual product.

  Cal.com (formerly Calendso) — small team
  Open-source scheduling tool competing with Calendly. Uses AGPL for the core with a commercial license for enterprise features. The AGPL prevents competitors from forking it into a proprietary product. They've
   grown significantly with this model.

  Sentry — started small, created FSL
  Started as a small open-source error tracking project. Grew over years. Eventually created the Functional Source License because they saw other projects getting burned and wanted a proactive solution rather
  than a reactive license change. Their reasoning is well-documented and worth reading — they explicitly designed FSL to be fair to both the developer and the community. The 2-year conversion to Apache 2.0 was
  a deliberate choice to build trust.

  HashiCorp — the cautionary tale that validates your concern
  Terraform, Vault, Consul were all MPL 2.0 (open source). AWS and others built managed services on top of HashiCorp's code without contributing back. HashiCorp switched to BSL in 2023. The community
  immediately forked Terraform into OpenTofu under the Linux Foundation. HashiCorp got the protection they wanted, but the fork meant they now compete against their own codebase maintained by a foundation with
  corporate backing. The lesson: switching licenses after building a large open-source community is painful. Choosing a protective license from the start avoids this entirely.

  Elasticsearch — same pattern, harder landing
  Elastic open-sourced Elasticsearch under Apache 2.0. AWS launched Amazon OpenSearch Service, a managed Elasticsearch offering. Elastic switched to SSPL (even more restrictive than BSL). AWS forked the last
  Apache-licensed version into OpenSearch. Elastic now competes against a well-funded fork of their own code. Again: starting with a protective license would have been much simpler than changing later.

  Redis — same pattern, third time
  Redis was BSD-licensed. AWS offered ElastiCache (managed Redis). Redis Labs changed the license for their modules to source-available, then eventually changed Redis itself. Same fork risk, same community
  friction.

  The pattern that emerges

  The companies that got hurt (Elastic, Redis, HashiCorp) all started with permissive open-source licenses, built large communities, then tried to change the terms after a big company exploited the opening. The
   license change protected them commercially but cost them community trust and spawned competing forks.

  The projects that did well (Sidekiq, Plausible, Cal.com, Sentry) either chose a protective license from the start or changed proactively before a crisis.

  For your situation — solo developer, starting a new project, no existing community expectations to violate — you're in the best possible position to choose a protective license from day one. Nobody can accuse
   you of a bait-and-switch because there's no switch. BSL or FSL from the start means anyone who adopts Crema knows the terms upfront.

  The Sidekiq model (solo dev, tiered commercial) is the closest analogue to your business structure, but Perham chose open core rather than BSL because Sidekiq predates BSL's existence. If he were starting
  today, BSL/FSL would likely be on his radar.

✻ Brewed for 49s

❯ are there similar models to bsl/fsl, having the code open source after 2 or 3 years gives a headstart but just postpones the problem

⏺ That's a sharp observation. The delayed conversion is BSL/FSL's biggest structural weakness — it doesn't solve the problem, it defers it. Once the code becomes Apache 2.0 or MIT, a well-funded company can
  take the 3-year-old version and build on it. For a fast-moving framework, 3-year-old code is stale. For infrastructure that changes slowly, it's still very usable.

  Here are models that provide permanent protection without the conversion clock:

  SSPL (Server Side Public License)
  Created by MongoDB. Like AGPL but stronger: if you offer the software as a service, you must open-source your entire stack — not just the modified code, but all management, monitoring, orchestration, and
  supporting tooling. This is designed to be so onerous that no cloud provider would comply; they'd buy a commercial license instead. The source is permanently available under these terms — no conversion to a
  permissive license. Downside: not OSI-approved, and the "entire stack" requirement is vaguely defined. Some view it as open source in name only.

  AGPL (GNU Affero General Public License)
  The strongest copyleft license that is still OSI-approved open source. Any modification or network use requires releasing source. For a framework, this is powerful: applications built with an AGPL framework
  may need to be AGPL themselves (legally debated, but the risk alone deters most commercial users from the free license). Those commercial users buy your commercial license instead. No expiration, no
  conversion. The protection is permanent. Qt, MongoDB (before SSPL), and Grafana (before BSL) all used or use this model.

  Source-Available with Custom Terms (no conversion)
  You write your own license that says: source is readable, users can modify for internal use, but cannot distribute competing products. Period. No conversion clause. This is what Confluent did with the
  Confluent Community License. The source stays visible forever, but the commercial restriction never expires. The downside is that custom licenses are unfamiliar — developers and legal teams have to actually
  read and evaluate them rather than recognizing a known license.

  Fair Source
  A broader movement/label that encompasses several of these approaches. The principle is: source is available, use is free up to a certain threshold (user count, revenue, etc.), commercial use beyond that
  threshold requires a paid license. No conversion. The MariaDB BSL is sometimes grouped here, but the fair source definition doesn't require eventual open-sourcing.

  Dual License (AGPL + Commercial) — the permanent version of BSL's intent
  This is arguably the cleanest model for what you want. The code is AGPL forever. Anyone can use it — if they accept AGPL terms. Companies that don't want copyleft obligations (which is most of them) pay for a
   commercial license. There's nothing to convert, nothing to expire. Your protection is the copyleft itself, not a time-limited clause.

  Practical comparison for your situation
  ┌─────────────────────────┬────────────────┬────────────────────┬─────────────────┬───────────────────┐
  │          Model          │ Source visible │ Protection expires │  OSI approved   │ Adoption friction │
  ├─────────────────────────┼────────────────┼────────────────────┼─────────────────┼───────────────────┤
  │ BSL/FSL                 │ Yes            │ Yes (2-4 years)    │ No              │ Low               │
  ├─────────────────────────┼────────────────┼────────────────────┼─────────────────┼───────────────────┤
  │ SSPL                    │ Yes            │ No                 │ No              │ Medium            │
  ├─────────────────────────┼────────────────┼────────────────────┼─────────────────┼───────────────────┤
  │ AGPL + Commercial       │ Yes            │ No                 │ Yes (AGPL part) │ Medium-High       │
  ├─────────────────────────┼────────────────┼────────────────────┼─────────────────┼───────────────────┤
  │ Custom source-available │ Yes            │ No                 │ No              │ High              │
  ├─────────────────────────┼────────────────┼────────────────────┼─────────────────┼───────────────────┤
  │ Proprietary             │ No             │ No                 │ No              │ Highest           │
  └─────────────────────────┴────────────────┴────────────────────┴─────────────────┴───────────────────┘
  The AGPL + Commercial dual license is probably the sweet spot if the conversion clause bothers you. It's the only model that gives you permanent protection, source transparency, OSI approval, and a proven
  monetization path (Qt, MongoDB's early years, Grafana's early years). The friction it creates — companies avoiding AGPL — is exactly the friction that drives license sales.

  The question to ask yourself: is the adoption friction from AGPL a bigger risk than the deferred exposure from BSL? For a new framework that needs to build a user base from zero, that's a real tension.
  BSL/FSL gets more people in the door now but leaves you exposed later. AGPL protects you permanently but makes the door narrower from day one.






Crema is a framework. The developer's application code runs inside Crema's Java runtime, calls Crema APIs, and relies on Crema's WebView bridge. This is much more tightly coupled than, say, using Sentry (a
  standalone tool) or PostgreSQL (a separate process your app talks to over a socket).

  Under AGPL, a strong argument exists that any app built with Crema is a derivative work of Crema. The developer's Java code links against Crema's runtime. The frontend JS calls Crema's bridge APIs. The whole
  application is a single process.

  This means in practice: nearly every commercial Crema user would need a commercial license. That's the desired monetization effect — but it creates friction at every stage.

  Scenario 1: Developer discovers Crema, wants to try it

  They find the project, think the architecture is interesting, and want to prototype something. They read the license: AGPL. They check the alternatives: Electron is MIT, Tauri is MIT/Apache. With Electron or
  Tauri they can start building immediately with zero legal considerations. With Crema they either accept that their prototype is now AGPL-encumbered, or they email you about commercial terms before writing a
  line of code.

  Many developers will just pick Tauri. Not because it's better technically, but because it has zero friction at the "let me try this on a Saturday afternoon" stage.




The conversion concern is worth stress-testing though. Ask yourself: in 3 years, what would a competitor actually have?

  They'd have a 3-year-old snapshot of Crema's runtime — no Platform services (those are proprietary/hosted), no ongoing maintenance, no Panama bindings for APIs that didn't exist 3 years ago, no support for
  whatever macOS/Windows version shipped last year. They'd have to fork it, staff a team to maintain it, keep up with three operating systems' native API changes, and build their own platform layer from
  scratch. That's not free code — that's a full-time job.

  The projects where BSL conversion actually hurt would be slow-moving infrastructure: a database engine, a protocol implementation, something where the 3-year-old code is still perfectly functional. For a
  desktop framework targeting native OS APIs that change yearly, the treadmill works in your favor.

  So the realistic risk isn't "AWS forks Crema 3.0 and ships Crema Enterprise." It's more like "a small company takes the converted code as a starting point for something adjacent." And at that point, you're 3
  years ahead with a fully integrated platform, a user base, and institutional knowledge of the codebase.

  The place where BSL/FSL doesn't protect you is if someone builds a clean-room competitor inspired by your ideas — but no license protects against that. Ideas aren't copyrightable.

  Given all of this, BSL with a 3-year conversion to Apache 2.0 seems like the pragmatic choice. Low adoption friction, meaningful protection during the window that matters, and the conversion clause is less
  dangerous than it appears for a fast-moving framework. FSL with 2 years is also fine — the shorter window is more community-friendly and the protection tradeoff is marginal for the same reasons.



