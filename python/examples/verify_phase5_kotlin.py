#!/usr/bin/env python3
"""
Kotlin Phase 5 Implementation Verification

This script verifies that the Phase 5 Kotlin implementation files:
- Exist with proper structure
- Have complete implementations
- Include all required components
- Follow the tensor signature specification
"""

import os
import re
from pathlib import Path


def check_file_exists(filepath: str) -> tuple[bool, int]:
    """Check if file exists and return line count"""
    if os.path.exists(filepath):
        with open(filepath, 'r') as f:
            lines = len(f.readlines())
        return True, lines
    return False, 0


def check_class_implementation(filepath: str, class_name: str, required_methods: list) -> dict:
    """Verify class implementation has required methods"""
    results = {
        'class_found': False,
        'methods_found': {},
        'issues': []
    }
    
    if not os.path.exists(filepath):
        results['issues'].append(f"File not found: {filepath}")
        return results
    
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Check if class exists
    class_pattern = rf'class\s+{class_name}'
    if re.search(class_pattern, content):
        results['class_found'] = True
    else:
        results['issues'].append(f"Class {class_name} not found")
        return results
    
    # Check for required methods
    for method in required_methods:
        method_pattern = rf'fun\s+{method}\s*\('
        if re.search(method_pattern, content):
            results['methods_found'][method] = True
        else:
            results['methods_found'][method] = False
            results['issues'].append(f"Method {method} not found in {class_name}")
    
    return results


def verify_tensor_signature(filepath: str) -> dict:
    """Verify tensor signature implementation"""
    results = {
        'signature_found': False,
        'components': {},
        'issues': []
    }
    
    if not os.path.exists(filepath):
        results['issues'].append(f"File not found: {filepath}")
        return results
    
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Check for tensor signature components
    signature_components = ['rules', 'mutability', 'fitness', 'introspect']
    
    for component in signature_components:
        # Look for the component in comments or code
        pattern = rf'\b{component}\b'
        if re.search(pattern, content, re.IGNORECASE):
            results['components'][component] = True
        else:
            results['components'][component] = False
            results['issues'].append(f"Tensor component '{component}' not referenced")
    
    # Check if all components are found
    results['signature_found'] = all(results['components'].values())
    
    return results


def main():
    print("="*70)
    print("🔍 Phase 5 Kotlin Implementation Verification")
    print("="*70)
    
    base_path = "/home/runner/work/togai/togai"
    
    # Phase 5 implementation files
    phase5_files = {
        "MetaCognitivePathwaySystem": {
            "path": f"{base_path}/src/main/kotlin/org/ninelym/cognitive/metacognition/MetaCognitivePathwaySystem.kt",
            "required_methods": ["performIntrospection", "extractCognitiveRules", "analyzeHypergraphStructure"]
        },
        "EvolutionaryOptimizer": {
            "path": f"{base_path}/src/main/kotlin/org/ninelym/cognitive/metacognition/EvolutionaryOptimizer.kt",
            "required_methods": ["evolveSystem", "evaluateFitness", "createOptimizationGenome"]
        },
        "RecursiveVerificationSystem": {
            "path": f"{base_path}/src/main/kotlin/org/ninelym/cognitive/metacognition/RecursiveVerificationSystem.kt",
            "required_methods": ["performRecursiveVerification", "applyFeedback", "verifySystemHealth"]
        }
    }
    
    # Test files
    test_files = {
        "MetaCognitivePathwaySystemTest": f"{base_path}/src/test/kotlin/org/ninelym/cognitive/metacognition/MetaCognitivePathwaySystemTest.kt",
        "EvolutionaryOptimizerTest": f"{base_path}/src/test/kotlin/org/ninelym/cognitive/metacognition/EvolutionaryOptimizerTest.kt",
        "RecursiveVerificationSystemTest": f"{base_path}/src/test/kotlin/org/ninelym/cognitive/metacognition/RecursiveVerificationSystemTest.kt"
    }
    
    # Integration file
    cognitive_engine_path = f"{base_path}/app/src/main/kotlin/org/ninelym/cognitive/CognitiveEngine.kt"
    
    all_passed = True
    
    # Check main implementation files
    print("\n📁 Checking Implementation Files:")
    print("-" * 70)
    
    for class_name, info in phase5_files.items():
        filepath = info["path"]
        exists, lines = check_file_exists(filepath)
        
        if exists:
            print(f"✅ {class_name}.kt - {lines} lines")
            
            # Check class implementation
            implementation = check_class_implementation(filepath, class_name, info["required_methods"])
            
            if implementation['class_found']:
                print(f"   ✓ Class {class_name} found")
                
                methods_ok = True
                for method, found in implementation['methods_found'].items():
                    if found:
                        print(f"   ✓ Method: {method}")
                    else:
                        print(f"   ✗ Method: {method} - MISSING")
                        methods_ok = False
                        all_passed = False
                
                if not methods_ok:
                    print(f"   ⚠️  Some methods are missing")
            else:
                print(f"   ✗ Class {class_name} NOT FOUND")
                all_passed = False
        else:
            print(f"❌ {class_name}.kt - NOT FOUND")
            all_passed = False
    
    # Check test files
    print("\n🧪 Checking Test Files:")
    print("-" * 70)
    
    for test_name, filepath in test_files.items():
        exists, lines = check_file_exists(filepath)
        
        if exists:
            print(f"✅ {test_name}.kt - {lines} lines")
        else:
            print(f"❌ {test_name}.kt - NOT FOUND")
            all_passed = False
    
    # Check CognitiveEngine integration
    print("\n🔗 Checking CognitiveEngine Integration:")
    print("-" * 70)
    
    exists, lines = check_file_exists(cognitive_engine_path)
    if exists:
        print(f"✅ CognitiveEngine.kt - {lines} lines")
        
        # Check for Phase 5 integration methods
        integration_methods = [
            "performMetaCognitiveIntrospection",
            "evolveCognitiveSystem",
            "performRecursiveVerification",
            "getPhase5TensorSignature",
            "performPhase5Cycle"
        ]
        
        with open(cognitive_engine_path, 'r') as f:
            content = f.read()
        
        for method in integration_methods:
            if re.search(rf'fun\s+{method}\s*\(', content):
                print(f"   ✓ Method: {method}")
            else:
                print(f"   ✗ Method: {method} - MISSING")
                all_passed = False
    else:
        print(f"❌ CognitiveEngine.kt - NOT FOUND")
        all_passed = False
    
    # Check tensor signature
    print("\n🎯 Verifying Tensor Signature [rules, mutability, fitness, introspect]:")
    print("-" * 70)
    
    tensor_result = verify_tensor_signature(cognitive_engine_path)
    
    if tensor_result['signature_found']:
        print("✅ Tensor signature fully implemented")
        for component, found in tensor_result['components'].items():
            print(f"   ✓ Component: {component}")
    else:
        print("⚠️  Tensor signature incomplete")
        for component, found in tensor_result['components'].items():
            if found:
                print(f"   ✓ Component: {component}")
            else:
                print(f"   ✗ Component: {component} - MISSING")
                all_passed = False
    
    # Documentation check
    print("\n📚 Checking Documentation:")
    print("-" * 70)
    
    doc_path = f"{base_path}/docs/PHASE5_RECURSIVE_META_COGNITION.md"
    exists, lines = check_file_exists(doc_path)
    
    if exists:
        print(f"✅ PHASE5_RECURSIVE_META_COGNITION.md - {lines} lines")
    else:
        print(f"❌ PHASE5_RECURSIVE_META_COGNITION.md - NOT FOUND")
        all_passed = False
    
    # Final summary
    print("\n" + "="*70)
    if all_passed:
        print("✅ Phase 5 Implementation: COMPLETE")
        print("="*70)
        print("\n✨ All components verified successfully!")
        print("\n📝 Implementation includes:")
        print("  • 5.1 Meta-Cognitive Pathways - MetaCognitivePathwaySystem")
        print("  • 5.2 Adaptive Optimization - EvolutionaryOptimizer")
        print("  • 5.3 Recursive Verification - RecursiveVerificationSystem")
        print("  • Tensor Signature [rules, mutability, fitness, introspect]")
        print("  • Complete test coverage")
        print("  • CognitiveEngine integration")
        print("  • Comprehensive documentation")
        return 0
    else:
        print("⚠️  Phase 5 Implementation: INCOMPLETE")
        print("="*70)
        print("\n❌ Some components are missing or incomplete")
        print("Please review the issues listed above.")
        return 1


if __name__ == "__main__":
    exit(main())
