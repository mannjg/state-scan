#!/usr/bin/env python3
"""
Compare state-scan output against ground truth to calculate precision/recall.
"""
import json
import re
import sys
from collections import defaultdict

def normalize_class_name(name):
    """Normalize class name for comparison."""
    # Remove inner class $ notation differences
    return name.replace('$', '.')

def create_key(finding):
    """Create a unique key for a finding."""
    class_name = normalize_class_name(finding.get('className', ''))
    field_name = finding.get('fieldName', '')
    return f"{class_name}::{field_name}"

def load_statescan_output(path):
    """Load state-scan JSON output."""
    with open(path) as f:
        data = json.load(f)

    findings = []
    for f in data.get('findings', []):
        findings.append({
            'className': f.get('className', ''),
            'fieldName': f.get('fieldName', ''),
            'fieldType': f.get('fieldType', ''),
            'pattern': f.get('pattern', ''),
            'riskLevel': f.get('riskLevel', ''),
            'raw': f
        })
    return findings

def load_ground_truth(path):
    """Load ground truth JSON."""
    with open(path) as f:
        return json.load(f)

def compare(statescan_findings, ground_truth):
    """Compare findings and calculate metrics."""
    # Create lookup sets
    statescan_keys = {create_key(f): f for f in statescan_findings}
    ground_truth_keys = {create_key(f): f for f in ground_truth}

    # Calculate matches
    true_positives = []
    false_positives = []
    false_negatives = []

    for key, finding in statescan_keys.items():
        if key in ground_truth_keys:
            true_positives.append((finding, ground_truth_keys[key]))
        else:
            false_positives.append(finding)

    for key, finding in ground_truth_keys.items():
        if key not in statescan_keys:
            false_negatives.append(finding)

    return true_positives, false_positives, false_negatives

def main():
    statescan_path = 'pulsar-statescan.json'
    ground_truth_path = 'ground-truth-merged.json'

    print("=" * 70)
    print("STATE-SCAN VALIDATION REPORT")
    print("=" * 70)
    print()

    # Load data
    statescan = load_statescan_output(statescan_path)
    ground_truth = load_ground_truth(ground_truth_path)

    print(f"State-scan findings: {len(statescan)}")
    print(f"Ground truth findings: {len(ground_truth)}")
    print()

    # Debug: show sample keys
    print("Sample state-scan keys:")
    for f in statescan[:3]:
        print(f"  {create_key(f)}")
    print()
    print("Sample ground truth keys:")
    for f in ground_truth[:3]:
        print(f"  {create_key(f)}")
    print()

    # Compare
    tp, fp, fn = compare(statescan, ground_truth)

    # Calculate metrics
    precision = len(tp) / (len(tp) + len(fp)) if (len(tp) + len(fp)) > 0 else 0
    recall = len(tp) / (len(tp) + len(fn)) if (len(tp) + len(fn)) > 0 else 0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) > 0 else 0

    print("METRICS:")
    print("-" * 40)
    print(f"True Positives:  {len(tp)}")
    print(f"False Positives: {len(fp)}")
    print(f"False Negatives: {len(fn)}")
    print()
    print(f"Precision: {precision:.2%}")
    print(f"Recall:    {recall:.2%}")
    print(f"F1 Score:  {f1:.2%}")
    print()

    # Show matched findings
    if tp:
        print("=" * 70)
        print("TRUE POSITIVES (Correctly detected)")
        print("=" * 70)
        for ss_finding, gt_finding in tp[:10]:
            print(f"  {ss_finding['className']}::{ss_finding['fieldName']}")
        print()

    # Show false negatives (missed by state-scan)
    if fn:
        print("=" * 70)
        print("FALSE NEGATIVES (Missed by state-scan)")
        print("=" * 70)
        for f in fn[:20]:  # Show first 20
            print(f"  {f['className']}::{f['fieldName']}")
            print(f"    Type: {f['fieldType']}")
            print(f"    Pattern: {f.get('pattern', 'N/A')}")
            print()

    # Show false positives (over-detection by state-scan)
    if fp:
        print("=" * 70)
        print(f"FALSE POSITIVES ({len(fp)} extra detections by state-scan)")
        print("=" * 70)
        # Group by class
        by_class = defaultdict(list)
        for f in fp:
            by_class[f['className']].append(f)

        for cls, findings in sorted(by_class.items())[:20]:
            print(f"  {cls}")
            for f in findings:
                print(f"    - {f['fieldName']}: {f.get('fieldType', 'N/A')}")
        print()

    # Summary by module
    print("=" * 70)
    print("GROUND TRUTH BY MODULE")
    print("=" * 70)
    by_module = defaultdict(int)
    for f in ground_truth:
        # Extract module from className
        cls = f['className']
        parts = cls.split('.')
        if 'pulsar' in parts:
            idx = parts.index('pulsar')
            if idx + 1 < len(parts):
                module = parts[idx + 1]
                by_module[module] += 1
        elif 'bookkeeper' in parts:
            by_module['bookkeeper'] += 1

    for module, count in sorted(by_module.items(), key=lambda x: -x[1]):
        print(f"  {module}: {count}")

if __name__ == '__main__':
    main()
