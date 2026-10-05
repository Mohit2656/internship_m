# Third-party attribution

This internal service uses the inference and document-processing API from
[`somus/resume-extract`](https://github.com/somus/resume-extract), pinned to
release `0.3.0` (reference commit `ce83da8d738d77d04f5b9fe70fc9861f6101fac2`).

It runs the [`oksomu/resume-ner`](https://huggingface.co/oksomu/resume-ner)
model locally. The model is a resume token-classification model based on
DistilBERT and is published under the Apache License 2.0. Model files are
downloaded into the ignored `models/` directory and are not committed here.

No resume content is sent to Hugging Face or any external AI provider. The
model repository is contacted only by the explicit `npm run model:setup`
developer command to download the model artifacts.
