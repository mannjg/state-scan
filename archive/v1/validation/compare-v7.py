#!/usr/bin/env python3
"""
Compare state-scan output against bytecode-generated ground truth.
V7: Uses validated-ground-truth.json from GroundTruthGenerator.
"""
import json
import sys
from collections import defaultdict

def normalize_class_name(name):
    """Normalize class name for comparison."""
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
    """Load bytecode-generated ground truth JSON."""
    with open(path) as f:
        data = json.load(f)

    # Handle nested groundTruth array
    if 'groundTruth' in data:
        return data['groundTruth']
    return data  # Fallback to flat list

def compare(statescan_findings, ground_truth):
    """Compare findings and calculate metrics."""
    statescan_keys = {create_key(f): f for f in statescan_findings}
    ground_truth_keys = {create_key(f): f for f in ground_truth}

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

def categorize_by_type(findings):
    """Categorize findings by field type pattern."""
    categories = defaultdict(list)
    for f in findings:
        field_type = f.get('fieldType', '')
        # Extract simple type name
        if 'ThreadLocal' in field_type or 'FastThreadLocal' in field_type:
            categories['ThreadLocal'].append(f)
        elif 'Counter' in field_type or 'Gauge' in field_type or 'Histogram' in field_type or 'Summary' in field_type:
            categories['Metrics'].append(f)
        elif 'Cache' in field_type or 'LoadingCache' in field_type:
            categories['Cache'].append(f)
        elif 'Map' in field_type or 'HashMap' in field_type or 'ConcurrentHashMap' in field_type:
            categories['Map'].append(f)
        elif 'List' in field_type or 'ArrayList' in field_type:
            categories['List'].append(f)
        elif 'Set' in field_type:
            categories['Set'].append(f)
        elif 'Atomic' in field_type:
            categories['Atomic'].append(f)
        elif 'Recorder' in field_type:
            categories['Recorder'].append(f)
        else:
            categories['Other'].append(f)
    return categories

def main():
    statescan_path = sys.argv[1] if len(sys.argv) > 1 else 'pulsar-statescan-v6.json'
    ground_truth_path = sys.argv[2] if len(sys.argv) > 2 else 'validated-ground-truth.json'

    print("=" * 70)
    print("STATE-SCAN VALIDATION REPORT (V7 - Bytecode Ground Truth)")
    print("=" * 70)
    print()

    # Load data
    statescan = load_statescan_output(statescan_path)
    ground_truth = load_ground_truth(ground_truth_path)

    print(f"State-scan findings:    {len(statescan)}")
    print(f"Ground truth findings:  {len(ground_truth)}")
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

    # Analyze false positives by category
    print("=" * 70)
    print("FALSE POSITIVES BY CATEGORY")
    print("=" * 70)
    fp_cats = categorize_by_type(fp)
    for cat, items in sorted(fp_cats.items(), key=lambda x: -len(x[1])):
        print(f"  {cat}: {len(items)}")
    print()

    # Analyze false negatives by category
    print("=" * 70)
    print("FALSE NEGATIVES BY CATEGORY")
    print("=" * 70)
    fn_cats = categorize_by_type(fn)
    for cat, items in sorted(fn_cats.items(), key=lambda x: -len(x[1])):
        print(f"  {cat}: {len(items)}")
    print()

    # Show true positives
    if tp:
        print("=" * 70)
        print(f"TRUE POSITIVES ({len(tp)} correctly detected)")
        print("=" * 70)
        for ss_finding, gt_finding in tp[:15]:
            print(f"  {ss_finding['className']}::{ss_finding['fieldName']}")
            print(f"    Type: {ss_finding.get('fieldType', 'N/A')[:60]}")
        if len(tp) > 15:
            print(f"  ... and {len(tp) - 15} more")
        print()

    # Show false negatives (missed by state-scan)
    if fn:
        print("=" * 70)
        print(f"FALSE NEGATIVES ({len(fn)} missed by state-scan)")
        print("=" * 70)
        for f in fn[:15]:
            print(f"  {f['className']}::{f['fieldName']}")
            print(f"    Type: {f.get('fieldType', 'N/A')}")
            print(f"    Pattern: {f.get('pattern', 'N/A')}")
        if len(fn) > 15:
            print(f"  ... and {len(fn) - 15} more")
        print()

    # Show false positives (extra detections)
    if fp:
        print("=" * 70)
        print(f"FALSE POSITIVES ({len(fp)} extra detections)")
        print("=" * 70)
        by_class = defaultdict(list)
        for f in fp:
            by_class[f['className']].append(f)

        for cls, findings in sorted(by_class.items())[:20]:
            print(f"  {cls}")
            for f in findings:
                print(f"    - {f['fieldName']}: {f.get('fieldType', 'N/A')[:50]}")
        if len(by_class) > 20:
            print(f"  ... and {len(by_class) - 20} more classes")
        print()

    # Summary comparison
    print("=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print(f"Ground truth: {len(ground_truth)} static mutable fields (bytecode-verified)")
    print(f"State-scan:   {len(statescan)} findings")
    print(f"Match rate:   {len(tp)} / {len(ground_truth)} = {recall:.1%} recall")
    print()

    if len(fp) > len(ground_truth):
        print("NOTE: State-scan finds MORE than ground truth because:")
        print("  1. Ground truth only counts fields matching specific mutable patterns")
        print("  2. State-scan may detect additional patterns (ThreadLocal, metrics, etc.)")
        print("  3. This may indicate ground truth needs expansion, not state-scan over-detection")

if __name__ == '__main__':
    main()
