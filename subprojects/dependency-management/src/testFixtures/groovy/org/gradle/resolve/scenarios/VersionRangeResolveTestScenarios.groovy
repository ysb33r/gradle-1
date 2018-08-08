/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.resolve.scenarios

import groovy.transform.Canonical
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint

/**
 * A comprehensive set of test cases for dependency resolution of a single module version, given a set of input selectors.
 */
class VersionRangeResolveTestScenarios {
    public static final REJECTED = "REJECTED"
    public static final FAILED = "FAILED"
    public static final IGNORE = "IGNORE"

    public static final EMPTY = empty()
    public static final FIXED_7 = fixed(7)
    public static final FIXED_9 = fixed(9)
    public static final FIXED_10 = fixed(10)
    public static final FIXED_11 = fixed(11)
    public static final FIXED_12 = fixed(12)
    public static final FIXED_13 = fixed(13)
    public static final PREFER_11 = prefer(11)
    public static final PREFER_12 = prefer(12)
    public static final PREFER_13 = prefer(13)
    public static final PREFER_7_8 = prefer(7, 8)
    public static final PREFER_10_11 = prefer(10, 11)
    public static final PREFER_10_12 = prefer(10, 12)
    public static final PREFER_10_14 = prefer(10, 14)
    public static final PREFER_14_16 = prefer(14, 16)
    public static final RANGE_7_8 = range(7, 8)
    public static final RANGE_10_11 = range(10, 11)
    public static final RANGE_10_12 = range(10, 12)
    public static final RANGE_10_14 = range(10, 14)
    public static final RANGE_10_16 = range(10, 16)
    public static final RANGE_11_12 = range(11, 12)
    public static final RANGE_12_14 = range(12, 14)
    public static final RANGE_13_14 = range(13, 14)
    public static final RANGE_14_16 = range(14, 16)

    public static final DYNAMIC_PLUS = dynamic('+')
    public static final DYNAMIC_LATEST = dynamic( 'latest.integration')

    public static final REJECT_11 = reject(11)
    public static final REJECT_12 = reject(12)
    public static final REJECT_13 = reject(13)

    public static final StrictPermutationsProvider SCENARIOS_EMPTY = StrictPermutationsProvider.check(
        versions: [EMPTY, FIXED_12],
        expected: "12"
    ).and(
        versions: [EMPTY, PREFER_12],
        expected: "12"
    ).and(
        versions: [EMPTY, RANGE_10_12],
        expected: "12"
    ).and(
        versions: [EMPTY, EMPTY],
        expected: ""
    ).and(
        versions: [EMPTY, EMPTY, FIXED_12],
        expected: "12"
    )

    public static final StrictPermutationsProvider SCENARIOS_PREFER = StrictPermutationsProvider.check(
        versions: [PREFER_11, PREFER_12],
        expected: "12"
    ).and(
        versions: [PREFER_11, PREFER_10_12],
        expected: "11"
    ).and(
        versions: [PREFER_12, PREFER_10_11],
        expected: "12"
    ).and(
        versions: [PREFER_11, PREFER_12, PREFER_10_14],
        expected: "12"
    ).and(
        versions: [PREFER_10_11, PREFER_10_12, PREFER_10_14],
        expected: "11"
    ).and(
        versions: [PREFER_10_11, PREFER_12, PREFER_10_14],
        expected: "12"
    ).and(
        versions: [PREFER_12, FIXED_11],
        expected: "11",
        expectedStrict: [IGNORE, "11"]
    ).and(
        versions: [PREFER_11, FIXED_12],
        expected: "12",
        expectedStrict: [IGNORE, "12"]
    ).and(
        versions: [PREFER_12, RANGE_10_11],
        expected: "11",
        expectedStrict: [IGNORE, "11"]
    ).and(
        versions: [PREFER_12, RANGE_10_12],
        expected: "12",
        expectedStrict: [IGNORE, "12"]
    ).and(
        versions: [PREFER_12, RANGE_10_14],
        expected: "12",
        expectedStrict: [IGNORE, "12"]
    ).and(
        versions: [PREFER_11, RANGE_12_14],
        expected: "13",
        expectedStrict: [IGNORE, "13"]
    ).and(
        versions: [PREFER_11, PREFER_12, RANGE_10_14],
        expected: "12",
        expectedStrict: [IGNORE, IGNORE, "12"]
    ).and(
        versions: [PREFER_11, PREFER_13, RANGE_10_12],
        expected: "11",
        expectedStrict: [IGNORE, IGNORE, "11"]
    ).and(
        versions: [PREFER_7_8, FIXED_12],  // No version satisfies the range [7,8]
        expected: FAILED
    ).and(
        versions: [PREFER_14_16, FIXED_12], // No version satisfies the range [14,16]
        expected: FAILED
    )

    public static final StrictPermutationsProvider SCENARIOS_TWO_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_7, FIXED_13],
        expected: "13",
        expectedStrict: [REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_12, FIXED_13],
        expected: "13",
        expectedStrict: [REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_10_11],
        expected: "12",
        expectedStrict: ["12", REJECTED],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_10_14],
        expected: "12",
        expectedStrict: ["12", "12"]
    ).and(
        versions: [FIXED_12, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_12, RANGE_7_8],  // No version satisfies the range [7,8]
        expected: FAILED,
        expectedStrict: [FAILED, FAILED]
    ).and(
        versions: [FIXED_12, RANGE_14_16], // No version satisfies the range [14,16]
        expected: FAILED,
        expectedStrict: [FAILED, FAILED]
    ).and(
        versions: [RANGE_10_11, FIXED_10],
        expected: "10",
        expectedStrict: ["10", "10"]
    ).and(
        versions: [RANGE_10_14, FIXED_13],
        expected: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [RANGE_10_14, RANGE_10_11],
        expected: "11",
        expectedStrict: ["11", "11"]
    ).and(
        versions: [RANGE_10_14, RANGE_10_16],
        expected: "13",
        expectedStrict: ["13", "13"]
    ).and(
        versions: [DYNAMIC_PLUS, FIXED_11],
        expected: "13",
        expectedStrict: [IGNORE, "11"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_PLUS, RANGE_10_12],
        expected: "13",
        expectedStrict: [IGNORE, "12"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_PLUS, RANGE_10_16],
        expected: "13",
        expectedStrict: [IGNORE, "13"]
    ).and(
        versions: [DYNAMIC_LATEST, FIXED_11],
        expected: "13",
        expectedStrict: [IGNORE, "11"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_LATEST, RANGE_10_12],
        expected: "13",
        expectedStrict: [IGNORE, "12"],
        conflicts: true
    ).and(
        versions: [DYNAMIC_LATEST, RANGE_10_16],
        expected: "13",
        expectedStrict: [IGNORE, "13"]
    )

    public static final StrictPermutationsProvider SCENARIOS_DEPENDENCY_WITH_REJECT = StrictPermutationsProvider.check(
        versions: [FIXED_12, REJECT_11],
        expected: "12"
    ).and(
        versions: [FIXED_12, REJECT_12],
        expected: REJECTED
    ).and(
        versions: [FIXED_12, REJECT_13],
        expected: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_11],
        expected: "12"
    ).and(
        versions: [RANGE_10_12, REJECT_12],
        expected: "11"
    ).and(
        versions: [RANGE_10_12, REJECT_13],
        expected: "12"
    )

    public static final StrictPermutationsProvider SCENARIOS_THREE_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_10, FIXED_12, FIXED_13],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_10, FIXED_12, RANGE_10_14],
        expected: "12",
        expectedStrict: [REJECTED, "12", "12"],
        conflicts: true
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_14],
        expected: "10",
        expectedStrict: ["10", "10", "10"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_10, RANGE_11_12, RANGE_10_14],
        expected: "12",
        expectedStrict: [REJECTED, "12", "12"],
        conflicts: true
    ).and(
        versions: [RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expected: "11",
        expectedStrict: ["11", "11", "11"]
    ).and(
        versions: [RANGE_10_11, RANGE_10_12, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, "13"],
        conflicts: true
    ).and(
        versions: [FIXED_10, FIXED_10, FIXED_12],
        expected: "12",
        expectedStrict: [REJECTED, REJECTED, "12"],
        conflicts: true
    ).and(
        versions: [FIXED_10, FIXED_12, RANGE_12_14],
        expected: "12",
        expectedStrict: [REJECTED, "12", "12"],
        conflicts: true
    )

    public static final StrictPermutationsProvider SCENARIOS_WITH_REJECT = StrictPermutationsProvider.check(
        versions: [FIXED_11, FIXED_12, REJECT_11],
        expected: "12",
        expectedStrict: [REJECTED, "12", IGNORE]
    ).and(
        versions: [FIXED_11, FIXED_12, REJECT_12],
        expected: REJECTED,
        expectedStrict: [REJECTED, REJECTED, IGNORE]
    ).and(
        versions: [FIXED_11, FIXED_12, REJECT_13],
        expected: "12",
        expectedStrict: [REJECTED, "12", IGNORE]
    ).and(
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_11],
        expected: "12",
        expectedStrict: ["12", "12", "12", IGNORE]
    ).and(
        ignore: "Will require resolving RANGE_10_14 with the knowledge that FIXED_12 rejects < '12'",
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_12],
        expected: "13",
    ).and(
        versions: [RANGE_10_14, RANGE_10_12, FIXED_12, REJECT_13],
        expected: "12",
        expectedStrict: ["12", "12", "12", IGNORE]
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_11],
        expected: "13",
        expectedStrict: [REJECTED, "13", IGNORE]
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_12],
        expected: "13",
        expectedStrict: [REJECTED, "13", IGNORE]
    ).and(
        versions: [RANGE_10_12, RANGE_13_14, REJECT_13],
        expected: REJECTED,
        expectedStrict: [REJECTED, REJECTED, IGNORE]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_11],
        expected: "10",
        expectedStrict: [REJECTED, "10", "10", IGNORE]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_12],
        expected: "11",
        expectedStrict: [REJECTED, "11", "11", IGNORE]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, REJECT_13],
        expected: "11",
        expectedStrict: [REJECTED, "11", "11", IGNORE]
    )

    public static final StrictPermutationsProvider SCENARIOS_FOUR_DEPENDENCIES = StrictPermutationsProvider.check(
        versions: [FIXED_9, FIXED_10, FIXED_11, FIXED_12],
        expected: "12",
        expectedStrict: [REJECTED, REJECTED, REJECTED, "12"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, FIXED_12, RANGE_12_14],
        expected: "12",
        expectedStrict: [REJECTED, REJECTED, "12", "12"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_13_14],
        expected: "13",
        expectedStrict: [REJECTED, REJECTED, REJECTED, "13"]
    ).and(
        versions: [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expected: "10",
        expectedStrict: ["10", "10", "10", "10"]
    ).and(
        versions: [FIXED_9, RANGE_10_11, RANGE_10_12, RANGE_10_14],
        expected: "11",
        expectedStrict: [REJECTED, "11", "11", "11"]
    )
    private static RenderableVersion empty() {
        def vs = new SimpleVersion()
        vs.version = ""
        return vs
    }
    private static RenderableVersion fixed(int version) {
        def vs = new SimpleVersion()
        vs.version = "${version}"
        return vs
    }

    private static RenderableVersion prefer(int version) {
        def vs = new PreferVersion()
        vs.version = "${version}"
        return vs
    }

    private static RenderableVersion prefer(int low, int high) {
        def vs = new PreferVersion()
        vs.version = "[${low},${high}]"
        return vs
    }

    private static RenderableVersion dynamic(String version) {
        def vs = new SimpleVersion()
        vs.version = version
        return vs
    }

    private static RenderableVersion range(int low, int high) {
        def vs = new SimpleVersion()
        vs.version = "[${low},${high}]"
        return vs
    }

    private static RenderableVersion reject(int version) {
        def vs = new RejectVersion()
        vs.version = version
        vs
    }

    private static RenderableVersion strict(RenderableVersion input) {
        def v = new StrictVersion()
        v.version = input.version
        v
    }

    interface RenderableVersion {
        String getVersion()

        VersionConstraint getVersionConstraint()

        String render()
    }

    static class SimpleVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            DefaultMutableVersionConstraint.withVersion(version)
        }

        @Override
        String render() {
            "'org:foo:${version}'"
        }

        @Override
        String toString() {
            return version ?: "''"
        }
    }

    static class StrictVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            def vc = new DefaultMutableVersionConstraint(version)
            vc.strictly(version)
            return vc
        }

        @Override
        String render() {
            return "('org:foo') { version { strictly '${version}' } }"
        }

        @Override
        String toString() {
            return "strictly(" + version + ")"
        }
    }

    static class PreferVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            def vc = new DefaultMutableVersionConstraint(version)
            vc.prefer(version)
            return vc
        }

        @Override
        String render() {
            return "('org:foo') { version { prefer '${version}' } }"
        }

        @Override
        String toString() {
            return "prefer(" + version + ")"
        }
    }

    static class RejectVersion implements RenderableVersion {
        String version

        @Override
        VersionConstraint getVersionConstraint() {
            DefaultImmutableVersionConstraint.of("", "", "", [version])
        }

        @Override
        String render() {
            "('org:foo') { version { reject '${version}' } }"
        }

        @Override
        String toString() {
            return "reject " + version
        }
    }

    static class StrictPermutationsProvider implements Iterable<Candidate> {
        private final List<Batch> batches = []
        private int batchCount

        static StrictPermutationsProvider check(Map config) {
            new StrictPermutationsProvider().and(config)
        }

        StrictPermutationsProvider and(Map config) {
            assert config.versions != null
            assert config.expected != null

            ++batchCount
            if (!config.ignore) {
                List<RenderableVersion> versions = config.versions
                String expected = config.expected
                List<String> expectedStrict = config.expectedStrict
                boolean expectConflict = config.conflicts as boolean
                List<Batch> iterations = []
                String batchName = config.description ?: "#${batchCount} (${versions})"
                iterations.add(new Batch(batchName, versions, expected, expectConflict))
                if (expectedStrict) {
                    versions.size().times { idx ->
                        def expectedStrictResolution = expectedStrict[idx]
                        if (expectedStrictResolution != IGNORE) {
                            iterations.add(new Batch(batchName, versions.withIndex().collect { RenderableVersion version, idx2 ->
                                if (idx == idx2) {
                                    strict(version)
                                } else {
                                    version
                                }
                            }, expectedStrictResolution, expectConflict))
                        }
                    }
                }
                batches.addAll(iterations)
            }

            this
        }

        @Override
        Iterator<Candidate> iterator() {
            new PermutationIterator()
        }

        @Canonical
        static class Batch {
            String batchName
            List<RenderableVersion> versions
            String expected
            boolean expectConflict
        }

        class PermutationIterator implements Iterator<Candidate> {
            Iterator<Batch> batchesIterator = batches.iterator()
            String currentBatch
            Iterator<List<RenderableVersion>> current
            String expected
            boolean expectConflictResolution

            @Override
            boolean hasNext() {
                batchesIterator.hasNext() || current?.hasNext()
            }

            @Override
            Candidate next() {
                if (current?.hasNext()) {
                    return new Candidate(batch: currentBatch, candidates: current.next() as RenderableVersion[], expected: expected, conflicts: expectConflictResolution)
                }
                Batch nextBatch = batchesIterator.next()
                expected = nextBatch.expected
                expectConflictResolution = nextBatch.expectConflict
                current = nextBatch.versions.permutations().iterator()
                currentBatch = nextBatch.batchName
                return next()
            }
        }

        static class Candidate {
            String batch
            RenderableVersion[] candidates
            String expected
            boolean conflicts

            @Override
            String toString() {
                batch + ": " + candidates.collect { it.toString() }.join(' & ') + " -> ${expected}"
            }
        }
    }
}
