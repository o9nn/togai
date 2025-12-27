"""
Tests for Toga Configuration Management
"""

import json
import tempfile
from pathlib import Path
import pytest
from python.helpers.toga_config import TogaConfig, ConfigManager


class TestTogaConfig:
    """Test TogaConfig dataclass"""

    def test_default_initialization(self):
        """Test that TogaConfig initializes with correct defaults"""
        config = TogaConfig()

        # Test personality trait defaults
        assert config.default_cheerfulness == 0.95
        assert config.default_obsessiveness == 0.90
        assert config.default_playfulness == 0.92
        assert config.default_chaos == 0.95
        assert config.default_vulnerability == 0.70

    def test_custom_initialization(self):
        """Test TogaConfig with custom values"""
        config = TogaConfig(
            default_cheerfulness=0.80, default_obsessiveness=0.75, memory_max_size=200
        )

        assert config.default_cheerfulness == 0.80
        assert config.default_obsessiveness == 0.75
        assert config.memory_max_size == 200

    def test_trait_bounds(self):
        """Test that trait values are within valid bounds"""
        config = TogaConfig()

        traits = [
            config.default_cheerfulness,
            config.default_obsessiveness,
            config.default_playfulness,
            config.default_chaos,
            config.default_vulnerability,
            config.default_identity_fluidity,
            config.default_twisted_love,
            config.default_cuteness_sensitivity,
        ]

        for trait in traits:
            assert 0.0 <= trait <= 1.0, f"Trait value {trait} out of bounds"


class TestConfigManager:
    """Test ConfigManager functionality"""

    def test_get_default_config(self):
        """Test getting default configuration"""
        config = ConfigManager.get_default_config()

        assert isinstance(config, TogaConfig)
        assert config.default_cheerfulness == 0.95
        assert config.memory_max_size == 100

    def test_save_and_load_config(self):
        """Test saving and loading configuration to/from file"""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_path = Path(tmpdir) / "test_config.json"

            # Create custom config
            original_config = TogaConfig(
                default_cheerfulness=0.85, memory_max_size=150, emotional_decay_rate=0.03
            )

            # Save config
            ConfigManager.save_config(original_config, config_path)

            # Verify file exists
            assert config_path.exists()

            # Load config
            loaded_config = ConfigManager.load_config(config_path)

            # Verify loaded config matches original
            assert loaded_config.default_cheerfulness == 0.85
            assert loaded_config.memory_max_size == 150
            assert loaded_config.emotional_decay_rate == 0.03

    def test_load_nonexistent_config(self):
        """Test loading config from nonexistent file returns default"""
        with tempfile.TemporaryDirectory() as tmpdir:
            config_path = Path(tmpdir) / "nonexistent.json"

            config = ConfigManager.load_config(config_path)

            # Should return default config
            assert isinstance(config, TogaConfig)
            assert config.default_cheerfulness == 0.95

    def test_validate_config_valid(self):
        """Test validation of valid configuration"""
        config = TogaConfig()
        is_valid, errors = ConfigManager.validate_config(config)

        assert is_valid
        assert len(errors) == 0

    def test_validate_config_invalid_traits(self):
        """Test validation catches invalid trait values"""
        config = TogaConfig(default_cheerfulness=1.5, default_obsessiveness=-0.1)

        is_valid, errors = ConfigManager.validate_config(config)

        assert not is_valid
        assert len(errors) > 0
        assert any("cheerfulness" in error.lower() for error in errors)
        assert any("obsessiveness" in error.lower() for error in errors)

    def test_validate_config_invalid_memory(self):
        """Test validation catches invalid memory settings"""
        config = TogaConfig(memory_max_size=-10)

        is_valid, errors = ConfigManager.validate_config(config)

        assert not is_valid
        assert any("memory" in error.lower() for error in errors)

    def test_config_to_dict(self):
        """Test converting config to dictionary"""
        config = TogaConfig(default_cheerfulness=0.88, memory_max_size=120)

        config_dict = ConfigManager.config_to_dict(config)

        assert isinstance(config_dict, dict)
        assert config_dict["default_cheerfulness"] == 0.88
        assert config_dict["memory_max_size"] == 120

    def test_config_from_dict(self):
        """Test creating config from dictionary"""
        config_dict = {
            "default_cheerfulness": 0.82,
            "default_obsessiveness": 0.77,
            "memory_max_size": 130,
        }

        config = ConfigManager.config_from_dict(config_dict)

        assert isinstance(config, TogaConfig)
        assert config.default_cheerfulness == 0.82
        assert config.default_obsessiveness == 0.77
        assert config.memory_max_size == 130

    def test_merge_configs(self):
        """Test merging two configurations"""
        base_config = TogaConfig(default_cheerfulness=0.90, memory_max_size=100)

        override_config = TogaConfig(default_cheerfulness=0.85, default_obsessiveness=0.80)

        merged_config = ConfigManager.merge_configs(base_config, override_config)

        # Override values should be used
        assert merged_config.default_cheerfulness == 0.85
        assert merged_config.default_obsessiveness == 0.80
        # Base values should be preserved where not overridden
        assert merged_config.memory_max_size == 100

    def test_config_serialization_roundtrip(self):
        """Test complete serialization roundtrip"""
        original_config = TogaConfig(
            default_cheerfulness=0.87,
            default_playfulness=0.91,
            memory_max_size=125,
            emotional_decay_rate=0.04,
        )

        # Convert to dict
        config_dict = ConfigManager.config_to_dict(original_config)

        # Convert back to config
        restored_config = ConfigManager.config_from_dict(config_dict)

        # Verify all values match
        assert restored_config.default_cheerfulness == original_config.default_cheerfulness
        assert restored_config.default_playfulness == original_config.default_playfulness
        assert restored_config.memory_max_size == original_config.memory_max_size
        assert restored_config.emotional_decay_rate == original_config.emotional_decay_rate


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
