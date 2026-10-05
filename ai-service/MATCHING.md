# Phase 4.7 semantic matching

The matching endpoint uses the local quantized `onnx-community/all-MiniLM-L6-v2-ONNX` model with normalized mean pooling and cosine similarity. The model is lazily initialized once and reused; every request batches its distinct professional text inputs into one embedding call.

`RELATED_SKILL_THRESHOLD` defaults to `0.72` and may be overridden with the environment variable of the same name. Calibration against the checked-in local model deliberately leaves ambiguous technology-neighbour pairs such as MySQL/PostgreSQL, Docker/Kubernetes, AWS/Azure, React/Angular, and Spring/Spring Boot unmatched at the default threshold. This avoids presenting broad ecosystem proximity as demonstrated skill evidence. Exact normalized matches are resolved first, semantic matches are one-to-one within required and preferred groups, and explicit alias guards prevent Java/JavaScript and C/C++/C# overmatching.

Role and experience evidence uses the separately configurable `MATCH_EVIDENCE_THRESHOLD` (default `0.52`). Similarity is raw cosine evidence, not confidence, probability, accuracy, hiring suitability, or hard eligibility. MiniLM limitations and model changes can alter cosine values, so changes to the model or thresholds require rerunning the real-model and adversarial tests.
