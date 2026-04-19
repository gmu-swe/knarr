#!/usr/bin/env bash
#
# Bootstrap the two non-Central Maven dependencies Knarr needs:
#
#   1. edu.gmu.swe.greensolver:green:1.0-SNAPSHOT (expression library)
#   2. green-local:microsoft-z3:4.8.9 (Z3 Java bindings matching the bundled
#      native lib at z3-4.8.9-x64-ubuntu-16.04/bin/)
#
# Leaves both in ~/.m2 so subsequent `mvn install` picks them up.
#
# Idempotent: skips whichever step is already done.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_DIR="${REPO_ROOT}/.github/scripts"
M2="${HOME}/.m2/repository"

echo "[bootstrap] M2=${M2}"

install_file() {
    local file="$1" gid="$2" aid="$3" ver="$4"
    local target="${M2}/${gid//./\/}/${aid}/${ver}/${aid}-${ver}.jar"
    if [[ -f "${target}" ]]; then
        echo "[bootstrap] ${gid}:${aid}:${ver} already installed"
        return 0
    fi
    if [[ ! -f "${file}" ]]; then
        echo "[bootstrap] missing source jar ${file}; skipping ${gid}:${aid}:${ver}"
        return 0
    fi
    mvn -B install:install-file \
        -DgeneratePom=true \
        -Dfile="${file}" \
        -DgroupId="${gid}" \
        -DartifactId="${aid}" \
        -Dversion="${ver}" \
        -Dpackaging=jar
}

install_z3_bindings() {
    local jar="${REPO_ROOT}/z3-4.8.9-x64-ubuntu-16.04/bin/com.microsoft.z3.jar"
    install_file "${jar}" "green-local" "microsoft-z3" "4.8.9"
}

install_green_solver() {
    local ver="1.0-SNAPSHOT"
    if [[ -f "${M2}/edu/gmu/swe/greensolver/green/${ver}/green-${ver}.jar" ]]; then
        echo "[bootstrap] edu.gmu.swe.greensolver:green:${ver} already installed"
        return 0
    fi
    local workdir
    workdir="$(mktemp -d)"
    trap 'rm -rf "${workdir}"' EXIT
    git clone --depth=1 https://github.com/gmu-swe/green-solver.git "${workdir}/green-solver"
    local gdir="${workdir}/green-solver/green"

    # Install bundled lib jars as local-m2 deps so the rewritten pom resolves.
    install_file "${gdir}/lib/apfloat.jar"            green-local apfloat       1.0
    install_file "${gdir}/lib/trove-3.1a1.jar"        green-local trove         3.1a1
    install_file "${gdir}/lib/libcvc3.jar"            green-local libcvc3       1.0
    install_file "${gdir}/lib/jedis-2.0.0.jar"        green-local jedis         2.0
    install_file "${gdir}/lib/commons-exec-1.2.jar"   green-local commons-exec  1.2
    install_file "${gdir}/lib/choco-solver-2.1.3.jar" green-local choco-solver  2.1.3

    # Swap in our pre-patched pom and build.
    cp "${SCRIPT_DIR}/green-pom.xml" "${gdir}/pom.xml"
    mvn -B -f "${gdir}/pom.xml" install -DskipTests
    echo "[bootstrap] installed edu.gmu.swe.greensolver:green:${ver}"
}

install_z3_bindings
install_green_solver

echo "[bootstrap] done"
