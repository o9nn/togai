#!/usr/bin/env python3
"""
Performance Benchmarking for Toga Personality System

This script benchmarks the performance of various Toga operations
to ensure optimal performance in production environments.
"""

import os
import statistics
import sys
import time
from pathlib import Path

# Add parent directory to path for imports
sys.path.insert(0, str(Path(__file__).parent.parent))

from helpers.toga_personality import (
    TogaEmotionalState,
    TogaPersonality,
    TogaPersonalityTensor,
    initialize_toga_personality,
)


# Operations that call the language model are slow by nature; keep their sample small
MODEL_ITERATIONS = int(os.environ.get("TOGA_BENCH_MODEL_ITERATIONS", "5"))


def benchmark_function(func, iterations=1000, warmup=100):
    """
    Benchmark a function with multiple iterations.

    Args:
        func: Function to benchmark
        iterations: Number of iterations to run
        warmup: Number of warmup iterations

    Returns:
        Dictionary with benchmark results
    """
    # Warmup
    for _ in range(warmup):
        func()

    # Actual benchmark
    times = []
    for _ in range(iterations):
        start = time.perf_counter()
        func()
        end = time.perf_counter()
        times.append((end - start) * 1000)  # Convert to milliseconds

    return {
        "mean": statistics.mean(times),
        "median": statistics.median(times),
        "stdev": statistics.stdev(times) if len(times) > 1 else 0,
        "min": min(times),
        "max": max(times),
        "iterations": iterations,
    }


def print_benchmark_results(name, results):
    """Print formatted benchmark results."""
    print(f"\n{name}:")
    print(f"  Mean:   {results['mean']:.4f} ms")
    print(f"  Median: {results['median']:.4f} ms")
    print(f"  StdDev: {results['stdev']:.4f} ms")
    print(f"  Min:    {results['min']:.4f} ms")
    print(f"  Max:    {results['max']:.4f} ms")
    print(f"  Iterations: {results['iterations']}")


def benchmark_initialization():
    """Benchmark personality initialization."""
    print("\n" + "=" * 70)
    print("INITIALIZATION BENCHMARKS")
    print("=" * 70)

    # Default initialization
    results = benchmark_function(lambda: initialize_toga_personality(), iterations=10000)
    print_benchmark_results("Default Initialization", results)

    # Custom initialization
    custom_traits = {
        "cheerfulness": 0.99,
        "chaos": 0.98,
        "playfulness": 0.95,
    }
    results = benchmark_function(
        lambda: initialize_toga_personality(custom_traits), iterations=10000
    )
    print_benchmark_results("Custom Initialization", results)


def benchmark_input_framing():
    """Benchmark input framing operations."""
    print("\n" + "=" * 70)
    print("INPUT FRAMING BENCHMARKS")
    print("=" * 70)

    toga = initialize_toga_personality()

    # Normal message
    results = benchmark_function(
        lambda: toga.frame_input("Normal message without triggers"), iterations=MODEL_ITERATIONS, warmup=1
    )
    print_benchmark_results("Normal Message Framing", results)

    # Cute trigger message
    results = benchmark_function(
        lambda: toga.frame_input("This is so cute and adorable!"), iterations=MODEL_ITERATIONS, warmup=1
    )
    print_benchmark_results("Cute Trigger Message Framing", results)


def benchmark_emotional_state():
    """Benchmark emotional state operations."""
    print("\n" + "=" * 70)
    print("EMOTIONAL STATE BENCHMARKS")
    print("=" * 70)

    toga = initialize_toga_personality()

    # State update
    results = benchmark_function(
        lambda: toga.update_emotional_state("excited", 0.9, 2.0, "test"), iterations=10000
    )
    print_benchmark_results("Emotional State Update", results)

    # State decay
    state = TogaEmotionalState(state="obsessed", intensity=0.9, duration=2.0)
    results = benchmark_function(lambda: state.decay(rate=0.1), iterations=10000)
    print_benchmark_results("Emotional State Decay", results)


def benchmark_commentary():
    """Benchmark commentary generation."""
    print("\n" + "=" * 70)
    print("COMMENTARY GENERATION BENCHMARKS")
    print("=" * 70)

    toga = initialize_toga_personality()

    contexts = ["success", "failure", "cute_detected", "general"]

    for context in contexts:
        results = benchmark_function(
            lambda: toga.add_commentary("Test content", context=context), iterations=MODEL_ITERATIONS, warmup=1
        )
        print_benchmark_results(f"Commentary ({context})", results)


def benchmark_full_interaction():
    """Benchmark full interaction processing."""
    print("\n" + "=" * 70)
    print("FULL INTERACTION BENCHMARKS")
    print("=" * 70)

    toga = initialize_toga_personality()

    # Normal interaction
    results = benchmark_function(
        lambda: toga.process_interaction("Hello, how are you?"), iterations=MODEL_ITERATIONS, warmup=1
    )
    print_benchmark_results("Full Interaction (Normal)", results)

    # Cute trigger interaction
    toga2 = initialize_toga_personality()
    results = benchmark_function(
        lambda: toga2.process_interaction("This is so cute!"), iterations=MODEL_ITERATIONS, warmup=1
    )
    print_benchmark_results("Full Interaction (Cute Trigger)", results)


def benchmark_serialization():
    """Benchmark serialization operations."""
    print("\n" + "=" * 70)
    print("SERIALIZATION BENCHMARKS")
    print("=" * 70)

    # Create a toga with some state
    toga = initialize_toga_personality()
    for i in range(3):
        toga.process_interaction(f"Message {i}")

    # Serialization
    results = benchmark_function(lambda: toga.to_dict(), iterations=5000)
    print_benchmark_results("Serialization (to_dict)", results)

    # Deserialization
    data = toga.to_dict()
    results = benchmark_function(lambda: TogaPersonality.from_dict(data), iterations=5000)
    print_benchmark_results("Deserialization (from_dict)", results)


def benchmark_memory_operations():
    """Benchmark memory-related operations."""
    print("\n" + "=" * 70)
    print("MEMORY OPERATION BENCHMARKS")
    print("=" * 70)

    # Memory with deque (optimized)
    toga = initialize_toga_personality()

    # Each interaction is a real model call, so this measures generation latency
    start = time.perf_counter()
    for i in range(MODEL_ITERATIONS):
        toga.process_interaction(f"Message {i}")
    end = time.perf_counter()

    total = (end - start) * 1000
    print(f"\n{MODEL_ITERATIONS} Interactions (model-generated replies):")
    print(f"  Total time: {total:.2f} ms")
    print(f"  Per interaction: {total/MODEL_ITERATIONS:.1f} ms")
    print(f"  Memory size: {len(toga.memory)} (max: {toga.memory.maxlen})")


def benchmark_trait_evolution():
    """Benchmark trait evolution operations."""
    print("\n" + "=" * 70)
    print("TRAIT EVOLUTION BENCHMARKS")
    print("=" * 70)

    tensor = TogaPersonalityTensor()

    # Successful evolution
    results = benchmark_function(
        lambda: tensor.evolve_trait("cheerfulness", 0.01), iterations=10000
    )
    print_benchmark_results("Trait Evolution (Success)", results)

    # Failed evolution (ethical constraint)
    results = benchmark_function(
        lambda: tensor.evolve_trait("no_actual_harm", -0.5), iterations=10000
    )
    print_benchmark_results("Trait Evolution (Ethical Rejection)", results)


def benchmark_inheritance():
    """Benchmark personality inheritance."""
    print("\n" + "=" * 70)
    print("INHERITANCE BENCHMARKS")
    print("=" * 70)

    parent = TogaPersonalityTensor()

    # 70% inheritance
    results = benchmark_function(lambda: parent.inherit(inheritance_factor=0.7), iterations=5000)
    print_benchmark_results("Inheritance (70% factor)", results)

    # 50% inheritance
    results = benchmark_function(lambda: parent.inherit(inheritance_factor=0.5), iterations=5000)
    print_benchmark_results("Inheritance (50% factor)", results)


def run_all_benchmarks():
    """Run all benchmarks."""
    print(
        """
╔══════════════════════════════════════════════════════════════════════╗
║                                                                      ║
║     TOGA PERSONALITY SYSTEM - PERFORMANCE BENCHMARKS                ║
║                                                                      ║
║     Measuring performance across all core operations                ║
║                                                                      ║
╚══════════════════════════════════════════════════════════════════════╝
    """
    )

    start_time = time.time()

    benchmark_initialization()
    benchmark_input_framing()
    benchmark_emotional_state()
    benchmark_commentary()
    benchmark_full_interaction()
    benchmark_serialization()
    benchmark_memory_operations()
    benchmark_trait_evolution()
    benchmark_inheritance()

    end_time = time.time()
    total_time = end_time - start_time

    print("\n" + "=" * 70)
    print("BENCHMARK SUMMARY")
    print("=" * 70)
    print(f"\nTotal benchmark time: {total_time:.2f} seconds")
    print("\n✓ All benchmarks completed successfully!")
    print("\nNote: framing, commentary and interactions call the configured language model,")
    print("so their timings above reflect that model's generation latency on this machine.")


if __name__ == "__main__":
    run_all_benchmarks()
