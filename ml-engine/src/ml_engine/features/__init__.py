"""ml_engine.features — training/serving 공용 feature 변환(versioned schema, 5B). 계약 밖
어떤 결측/미지 값도 조용히 접지 않는다(v2-지침서.md §5) — DB·HTTP·업무 모듈을 모른다.

공개 표면 재수출(scope.md ①~⑥) — training(5C)·serving(5D)·registry 는 이 패키지의 최상위
이름만 보고 하위 모듈(`ml_engine.features.rows` 등)을 직접 import 하지 않는다.
"""

from __future__ import annotations

from ml_engine.features.encoding import (
    SHIPPED_ENCODING_POLICY,
    AgencyTargetEncoding,
    AwardRateObservation,
    EncodingPolicy,
    build_agency_target_encoding,
)
from ml_engine.features.facts import (
    FactRejected,
    FactRejectionReason,
    FactValue,
    FeatureFacts,
    Missing,
    Present,
)
from ml_engine.features.manifest import (
    ChecksumMismatch,
    FeatureManifest,
    ManifestAgencyMean,
    ManifestCategoryMean,
    ManifestColumn,
    canonical_json,
    compute_checksum,
    verify_manifest,
)
from ml_engine.features.manifest import (
    Verified as ManifestVerified,
)
from ml_engine.features.normalize import normalize_feature_key
from ml_engine.features.rows import (
    AwardRateFeatureSpace,
    ColumnProvenance,
    FeatureRow,
    MissingColumn,
    MissingFact,
    Observed,
    RowProvenance,
    RowRejected,
)
from ml_engine.features.schema import (
    FEATURE_SCHEMA_V2,
    SUPPORTED_FEATURE_SCHEMAS,
    ClosedRange,
    FeatureColumn,
    FeatureKind,
    FeatureSchema,
    MissingPolicy,
    NameMismatch,
    Undeclared,
    UnsupportedSchema,
    require_declared,
    resolve_schema,
    verify_feature_names,
)
from ml_engine.features.schema import (
    Verified as SchemaVerified,
)
from ml_engine.features.shrinkage import pseudo_count_weight, shrink_toward
from ml_engine.features.vocabulary import (
    OutOfVocabulary,
    Vocabulary,
    denominator_source_label_name,
    denominator_source_vocabulary,
)

__all__ = [
    "FEATURE_SCHEMA_V2",
    "SHIPPED_ENCODING_POLICY",
    "SUPPORTED_FEATURE_SCHEMAS",
    "AgencyTargetEncoding",
    "AwardRateFeatureSpace",
    "AwardRateObservation",
    "ChecksumMismatch",
    "ClosedRange",
    "ColumnProvenance",
    "EncodingPolicy",
    "FactRejected",
    "FactRejectionReason",
    "FactValue",
    "FeatureColumn",
    "FeatureFacts",
    "FeatureKind",
    "FeatureManifest",
    "FeatureRow",
    "FeatureSchema",
    "ManifestAgencyMean",
    "ManifestCategoryMean",
    "ManifestColumn",
    "ManifestVerified",
    "Missing",
    "MissingColumn",
    "MissingFact",
    "MissingPolicy",
    "NameMismatch",
    "Observed",
    "OutOfVocabulary",
    "Present",
    "RowProvenance",
    "RowRejected",
    "SchemaVerified",
    "Undeclared",
    "UnsupportedSchema",
    "Vocabulary",
    "build_agency_target_encoding",
    "canonical_json",
    "compute_checksum",
    "denominator_source_label_name",
    "denominator_source_vocabulary",
    "normalize_feature_key",
    "pseudo_count_weight",
    "require_declared",
    "resolve_schema",
    "shrink_toward",
    "verify_feature_names",
    "verify_manifest",
]
