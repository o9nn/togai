"""
Integration Verification System

Comprehensive testing suite for Phase 4 components:
- API endpoint testing
- Embodiment binding verification
- Performance benchmarking
- Health monitoring
"""

import time
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional


# ============================================================================
# Data Classes
# ============================================================================


@dataclass
class TestResult:
    """Individual test result."""
    
    test_name: str
    passed: bool
    duration_ms: float
    error_message: Optional[str] = None


@dataclass
class VerificationReport:
    """Complete verification report."""
    
    overall_health: float  # 0-1
    api_tests: List[TestResult]
    embodiment_tests: List[TestResult]
    performance_metrics: Dict[str, Any]
    health_checks: Dict[str, str]
    recommendations: List[str]
    timestamp: float


# ============================================================================
# Integration Verification System
# ============================================================================


class IntegrationVerificationSystem:
    """
    Comprehensive testing and verification system for Phase 4.
    
    Tests all API endpoints, embodiment bindings, and system health.
    """
    
    def __init__(self, mesh_api, unity3d_binding, ros_binding, web_agent_binding):
        """
        Initialize verification system.
        
        Args:
            mesh_api: CognitiveMeshAPI instance
            unity3d_binding: Unity3DBinding instance
            ros_binding: ROSBinding instance
            web_agent_binding: WebAgentBinding instance
        """
        self.mesh_api = mesh_api
        self.unity3d = unity3d_binding
        self.ros = ros_binding
        self.web = web_agent_binding
        
        print("[IntegrationVerification] Initialized")
    
    def run_complete_verification(self) -> VerificationReport:
        """
        Run complete verification of all Phase 4 components.
        
        Returns:
            VerificationReport with test results
        """
        print("\n" + "=" * 70)
        print("  Phase 4: Distributed Cognitive Mesh - Integration Verification")
        print("=" * 70 + "\n")
        
        # Run all test categories
        api_tests = self._run_api_tests()
        embodiment_tests = self._run_embodiment_tests()
        performance_metrics = self._run_performance_tests()
        health_checks = self._run_health_checks()
        
        # Calculate overall health
        passed_tests = sum(1 for t in api_tests + embodiment_tests if t.passed)
        total_tests = len(api_tests) + len(embodiment_tests)
        overall_health = passed_tests / total_tests if total_tests > 0 else 0.0
        
        # Generate recommendations
        recommendations = self._generate_recommendations(
            api_tests, embodiment_tests, performance_metrics, health_checks
        )
        
        # Create report
        report = VerificationReport(
            overall_health=overall_health,
            api_tests=api_tests,
            embodiment_tests=embodiment_tests,
            performance_metrics=performance_metrics,
            health_checks=health_checks,
            recommendations=recommendations,
            timestamp=time.time(),
        )
        
        # Print summary
        self._print_report_summary(report)
        
        return report
    
    def _run_api_tests(self) -> List[TestResult]:
        """Run API endpoint tests."""
        print("=" * 70)
        print("  4.1 API & Endpoint Engineering - Tests")
        print("=" * 70 + "\n")
        
        tests = []
        
        # Test 1: Mesh status endpoint
        start = time.time()
        try:
            status = self.mesh_api.get_mesh_status()
            passed = hasattr(status, 'agents') and hasattr(status, 'state')
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("mesh_status", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 2: Agent registration
        start = time.time()
        try:
            registration = self.mesh_api.register_agent(
                "test_agent",
                ["test_capability"],
                "generic"
            )
            passed = registration.success and registration.agent_id
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("agent_registration", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 3: Sensor data submission
        start = time.time()
        try:
            # Use the agent ID from previous test
            result_data = self.mesh_api.submit_sensor_data(
                registration.agent_id if passed else "test_agent_123",
                "test_sensor",
                {"value": 42}
            )
            passed = result_data.get("success", False)
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("sensor_data_submission", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 4: Effector actions
        start = time.time()
        try:
            result_data = self.mesh_api.get_effector_actions(
                registration.agent_id if 'registration' in locals() else "test_agent_123"
            )
            passed = result_data.get("success", False)
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("effector_actions", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        print()
        return tests
    
    def _run_embodiment_tests(self) -> List[TestResult]:
        """Run embodiment binding tests."""
        print("=" * 70)
        print("  4.2 Embodiment Bindings - Tests")
        print("=" * 70 + "\n")
        
        tests = []
        
        # Unity3D tests
        print("Unity3D Binding Tests:")
        print("-" * 40)
        
        # Test 1: Unity client connection
        start = time.time()
        try:
            from helpers.phase4.embodiment.unity3d_binding import Vector3, UnityTransform
            
            connection = self.unity3d.connect_unity_client(
                "test_unity_client",
                ["Transform", "Physics"],
                "TestScene"
            )
            passed = connection.success
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("unity3d_connection", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 2: Unity transform update
        start = time.time()
        try:
            if passed:
                transform = UnityTransform(
                    Vector3(1.0, 0.0, 0.0),
                    Vector3.zero(),
                    Vector3.one()
                )
                result_data = self.unity3d.update_game_object_transform(
                    connection.agent_id,
                    "TestObject",
                    transform
                )
                passed = result_data.get("success", False)
                error = None
            else:
                passed = False
                error = "Previous test failed"
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("unity3d_transform_update", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 3: Unity event handling
        start = time.time()
        try:
            from helpers.phase4.embodiment.unity3d_binding import UnityEventType
            
            if 'connection' in locals() and connection.success:
                result_data = self.unity3d.handle_unity_event(
                    connection.agent_id,
                    UnityEventType.COLLISION,
                    {"collider": "TestCollider", "force": 15.0}
                )
                passed = result_data.get("success", False)
                error = None
            else:
                passed = False
                error = "Connection not established"
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("unity3d_event_handling", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        print()
        
        # ROS tests
        print("ROS Binding Tests:")
        print("-" * 40)
        
        # Test 4: ROS node connection
        start = time.time()
        try:
            ros_connection = self.ros.connect_ros_node(
                "test_ros_node",
                ["navigation", "perception"],
                "ROS2"
            )
            passed = ros_connection.success
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("ros_connection", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 5: ROS topic publishing
        start = time.time()
        try:
            if passed:
                result_data = self.ros.publish_to_topic(
                    ros_connection.agent_id,
                    "/cmd_vel",
                    "geometry_msgs/Twist",
                    {"linear": {"x": 0.5, "y": 0.0, "z": 0.0}}
                )
                passed = result_data.get("success", False)
                error = None
            else:
                passed = False
                error = "Connection not established"
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("ros_topic_publishing", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 6: ROS sensor processing
        start = time.time()
        try:
            from helpers.phase4.embodiment.ros_binding import ROSSensorType
            
            if 'ros_connection' in locals() and ros_connection.success:
                result_data = self.ros.process_sensor_data(
                    ros_connection.agent_id,
                    ROSSensorType.LASER_SCAN,
                    {"ranges": [2.5, 1.8, 0.5, 4.0]}
                )
                passed = result_data.get("success", False)
                error = None
            else:
                passed = False
                error = "Connection not established"
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("ros_sensor_processing", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        print()
        
        # Web Agent tests
        print("Web Agent Binding Tests:")
        print("-" * 40)
        
        # Test 7: Web client connection
        start = time.time()
        try:
            web_connection = self.web.connect_web_client(
                "test_web_client",
                ["DOM", "Storage"],
                "TestAgent/1.0"
            )
            passed = web_connection.success
            error = None
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("web_client_connection", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 8: DOM interaction handling
        start = time.time()
        try:
            from helpers.phase4.embodiment.web_agent_binding import (
                DOMInteraction,
                DOMInteractionType,
            )
            
            if passed:
                interaction = DOMInteraction(
                    type=DOMInteractionType.CLICK,
                    element_id="test_button",
                    element_data={"tagName": "button", "className": "btn"}
                )
                result_data = self.web.handle_dom_interaction(
                    web_connection.agent_id,
                    interaction
                )
                passed = result_data.get("success", False)
                error = None
            else:
                passed = False
                error = "Connection not established"
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("web_dom_interaction", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        # Test 9: JavaScript SDK generation
        start = time.time()
        try:
            if 'web_connection' in locals() and web_connection.success:
                sdk = self.web.generate_javascript_sdk(web_connection.agent_id)
                passed = sdk and "CognitiveMeshWebSDK" in sdk
                error = None
            else:
                passed = False
                error = "Connection not established"
        except Exception as e:
            passed = False
            error = str(e)
        
        duration = (time.time() - start) * 1000
        result = TestResult("web_sdk_generation", passed, duration, error)
        tests.append(result)
        self._print_test_result(result)
        
        print()
        return tests
    
    def _run_performance_tests(self) -> Dict[str, Any]:
        """Run performance benchmarks."""
        print("=" * 70)
        print("  Performance Benchmarks")
        print("=" * 70 + "\n")
        
        metrics = {}
        
        # API response time
        start = time.time()
        for _ in range(10):
            self.mesh_api.get_mesh_status()
        avg_time = ((time.time() - start) / 10) * 1000
        metrics["api_response_time_ms"] = round(avg_time, 2)
        print(f"  • API Response Time: {metrics['api_response_time_ms']}ms (target: <100ms)")
        
        # Agent registration throughput
        start = time.time()
        for i in range(10):
            self.mesh_api.register_agent(f"perf_test_{i}", ["test"], "generic")
        registrations_per_sec = 10 / (time.time() - start)
        metrics["agent_registration_per_sec"] = round(registrations_per_sec, 2)
        print(f"  • Agent Registration: {metrics['agent_registration_per_sec']}/sec (target: >10/sec)")
        
        # Memory usage (simplified)
        import sys
        memory_kb = sys.getsizeof(self.mesh_api.agents) / 1024
        metrics["memory_usage_kb"] = round(memory_kb, 2)
        print(f"  • Memory Usage: {metrics['memory_usage_kb']}KB (target: <512MB)")
        
        print()
        return metrics
    
    def _run_health_checks(self) -> Dict[str, str]:
        """Run system health checks."""
        print("=" * 70)
        print("  System Health Checks")
        print("=" * 70 + "\n")
        
        health = {}
        
        # API health
        try:
            status = self.mesh_api.get_mesh_status()
            health["mesh_api"] = "✅ API operational"
        except Exception as e:
            health["mesh_api"] = f"❌ API error: {str(e)}"
        
        # Unity3D binding health
        try:
            conn = self.unity3d.connect_unity_client("health_check", ["test"], "test")
            health["unity3d_binding"] = "✅ Unity binding operational"
        except Exception as e:
            health["unity3d_binding"] = f"❌ Unity error: {str(e)}"
        
        # ROS binding health
        try:
            conn = self.ros.connect_ros_node("health_check", ["test"], "ROS2")
            health["ros_binding"] = "✅ ROS binding operational"
        except Exception as e:
            health["ros_binding"] = f"❌ ROS error: {str(e)}"
        
        # Web agent binding health
        try:
            conn = self.web.connect_web_client("health_check", ["test"], "test")
            health["web_agent_binding"] = "✅ Web binding operational"
        except Exception as e:
            health["web_agent_binding"] = f"❌ Web error: {str(e)}"
        
        for component, status in health.items():
            print(f"  • {component}: {status}")
        
        print()
        return health
    
    def _generate_recommendations(
        self,
        api_tests: List[TestResult],
        embodiment_tests: List[TestResult],
        performance_metrics: Dict[str, Any],
        health_checks: Dict[str, str],
    ) -> List[str]:
        """Generate recommendations based on test results."""
        recommendations = []
        
        # Check test failures
        failed_tests = [t for t in api_tests + embodiment_tests if not t.passed]
        if failed_tests:
            recommendations.append(
                f"⚠️  {len(failed_tests)} test(s) failed - review error messages"
            )
        
        # Check performance
        if performance_metrics.get("api_response_time_ms", 0) > 100:
            recommendations.append(
                "⚠️  API response time exceeds target - consider caching or optimization"
            )
        
        # Check health
        unhealthy = [k for k, v in health_checks.items() if "❌" in v]
        if unhealthy:
            recommendations.append(
                f"⚠️  {len(unhealthy)} component(s) unhealthy - review logs"
            )
        
        if not recommendations:
            recommendations.append("✅ All systems operational - no recommendations")
        
        return recommendations
    
    def _print_test_result(self, result: TestResult):
        """Print formatted test result."""
        status = "✅" if result.passed else "❌"
        print(f"  {status} {result.test_name}: {'Test passed' if result.passed else 'Test failed'} ({result.duration_ms:.2f}ms)")
        if result.error_message:
            print(f"     Error: {result.error_message}")
    
    def _print_report_summary(self, report: VerificationReport):
        """Print formatted report summary."""
        print("=" * 70)
        print("  Verification Summary")
        print("=" * 70 + "\n")
        
        passed_api = sum(1 for t in report.api_tests if t.passed)
        passed_embodiment = sum(1 for t in report.embodiment_tests if t.passed)
        
        print(f"API Tests: {passed_api}/{len(report.api_tests)} passed")
        print(f"Embodiment Tests: {passed_embodiment}/{len(report.embodiment_tests)} passed")
        print(f"Overall Health: {report.overall_health * 100:.0f}%")
        print()
        
        print("Recommendations:")
        for rec in report.recommendations:
            print(f"  {rec}")
        print()
        
        print("=" * 70)
        print(f"  Phase 4 Verification Complete - Health: {report.overall_health * 100:.0f}%")
        print("=" * 70 + "\n")
