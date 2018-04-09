# -*- encoding: utf-8

import pytest

from travistooling.decisionmaker import (
    does_file_affect_build_job,
    should_run_job
)
from travistooling.decisions import (
    IgnoredFileFormat,
    IgnoredPath,
    KnownAffectsThisJob,
    KnownDoesNotAffectThisJob,
    UnrecognisedFile
)


@pytest.mark.parametrize('path, task_name, exc_class, is_significant', [
    # Travis format task
    ('misc/myscript.py', 'travis-format', KnownAffectsThisJob, True),
    ('ontologies/item.ttl', 'travis-format', KnownAffectsThisJob, True),
    ('main.tf', 'travis-format', KnownAffectsThisJob, True),
    ('example.json', 'travis-format', KnownAffectsThisJob, True),
    ('README.md', 'travis-format', IgnoredFileFormat, False),

    # Unrecognised filenames fall through to being significant, regardless
    # of the build job.
    ('foo.txt', 'loris-build', UnrecognisedFile, True),
    ('foo.txt', 'api-test', UnrecognisedFile, True),
    ('foo.txt', 'snapshot_convertor-publish', UnrecognisedFile, True),

    # Certain file formats are always excluded.
    ('foo.md', 'ingestor-build', IgnoredFileFormat, False),
    ('image.png', 'reindex_worker-test', IgnoredFileFormat, False),
    ('ontology.graffle', 'nginx-test', IgnoredFileFormat, False),

    # Terraform files are significant, but only in the travis-format task
    ('s3.tf', 'elasticdump-test', IgnoredFileFormat, False),

    # Certain paths are always insignificant.
    ('LICENSE', 'travistooling-test', IgnoredPath, False),

    # Ontology/misc are significant, but only in the travis-format task
    ('misc/myscript.py', 'sierra_reader-build', IgnoredPath, False),
    ('ontologies/work.ttl', 'monitoring-publish', IgnoredPath, False),

    # Changes that belong exclusively to a single task
    ('loris/loris/Dockerfile', 'loris-build', KnownAffectsThisJob, True),
    ('loris/loris/Dockerfile', 'ingestor-test', KnownDoesNotAffectThisJob, False),
    ('sierra_adapter/sierra_reader/foo.scala', 'sierra_reader-publish', KnownAffectsThisJob, True),
    ('catalogue_pipeline/ingestor/bar.scala', 'api-test', KnownDoesNotAffectThisJob, False),

    # Anything in the sierra_adapter directory/common lib
    ('sierra_adapter/common/main.scala', 'loris-test', KnownDoesNotAffectThisJob, False),
    ('sierra_adapter/common/main.scala', 's3_demultiplexer-test', KnownDoesNotAffectThisJob, False),
    ('sierra_adapter/common/main.scala', 'sierra_window_generator-test', KnownDoesNotAffectThisJob, False),
    ('sbt_common/display/model.scala', 'id_minter-test', KnownAffectsThisJob, True),
    ('sbt_common/display/model.scala', 'loris-publish', KnownDoesNotAffectThisJob, False),
    ('sbt_common/display/model.scala', 'sierra_adapter-publish', UnrecognisedFile, True),
])
def test_does_file_affect_build_job(path, task_name, exc_class, is_significant):
    with pytest.raises(exc_class) as err:
        does_file_affect_build_job(path=path, task_name=task_name)
    assert err.value.is_significant == is_significant


def test_should_run_job_with_no_important_changes():
    result = should_run_job(changed_paths=[], task_name='loris-test')
    assert result == (False, {False: {}, True: {}})


def test_should_not_run_job_with_no_relevant_changes():
    result = should_run_job(
        changed_paths=['sierra_adapter/common/main.scala'],
        task_name='loris-test'
    )
    assert result == (False, {
        False: {KnownDoesNotAffectThisJob: set(['sierra_adapter/common/main.scala'])},
        True: {}
    })


def test_should_run_job_with_relevant_changes():
    result = should_run_job(
        changed_paths=[
            'sierra_adapter/common/main.scala',
            'loris/loris/Dockerfile',
        ],
        task_name='loris-test'
    )
    assert result == (True, {
        False: {KnownDoesNotAffectThisJob: set(['sierra_adapter/common/main.scala'])},
        True: {KnownAffectsThisJob: set(['loris/loris/Dockerfile'])}
    })
