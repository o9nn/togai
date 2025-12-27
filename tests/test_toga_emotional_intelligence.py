"""
Tests for Toga Emotional Intelligence Module
"""

import pytest
from python.helpers.toga_emotional_intelligence import (
    EmotionCategory,
    SentimentPolarity,
    EmotionalContext,
    EmpatheticResponse,
    EmotionalIntelligenceEngine,
)


class TestEmotionCategory:
    """Test EmotionCategory enum."""

    def test_all_categories_exist(self):
        """Test that all expected emotion categories exist."""
        expected = ["JOY", "SADNESS", "ANGER", "FEAR", "SURPRISE", "DISGUST", "TRUST", "ANTICIPATION"]
        actual = [e.name for e in EmotionCategory]
        assert set(actual) == set(expected)


class TestSentimentPolarity:
    """Test SentimentPolarity enum."""

    def test_all_polarities_exist(self):
        """Test that all sentiment polarities exist."""
        expected = ["VERY_POSITIVE", "POSITIVE", "NEUTRAL", "NEGATIVE", "VERY_NEGATIVE"]
        actual = [s.name for s in SentimentPolarity]
        assert set(actual) == set(expected)


class TestEmotionalContext:
    """Test EmotionalContext dataclass."""

    def test_initialization_full(self):
        """Test full initialization of emotional context."""
        context = EmotionalContext(
            primary_emotion=EmotionCategory.JOY,
            secondary_emotion=EmotionCategory.SURPRISE,
            intensity=0.8,
            sentiment=SentimentPolarity.POSITIVE,
            confidence=0.9,
            keywords=["happy", "excited"],
        )
        assert context.primary_emotion == EmotionCategory.JOY
        assert context.secondary_emotion == EmotionCategory.SURPRISE
        assert context.intensity == 0.8


class TestEmpatheticResponse:
    """Test EmpatheticResponse dataclass."""

    def test_initialization(self):
        """Test empathetic response initialization."""
        response = EmpatheticResponse(
            acknowledgment="I hear you",
            validation="That makes sense",
            support="I'm here for you",
            tone_adjustment=-0.3,
        )
        assert response.acknowledgment == "I hear you"
        assert response.tone_adjustment == -0.3


class TestEmotionalIntelligenceEngine:
    """Test EmotionalIntelligenceEngine class."""

    def test_initialization(self):
        """Test engine initialization."""
        engine = EmotionalIntelligenceEngine()
        assert engine.emotion_keywords is not None
        assert engine.sentiment_keywords is not None
        assert len(engine.emotion_keywords) > 0
        assert len(engine.sentiment_keywords) > 0

    def test_emotion_keywords_structure(self):
        """Test emotion keywords are properly structured."""
        engine = EmotionalIntelligenceEngine()
        assert EmotionCategory.JOY in engine.emotion_keywords
        assert EmotionCategory.SADNESS in engine.emotion_keywords
        assert isinstance(engine.emotion_keywords[EmotionCategory.JOY], list)

    def test_sentiment_keywords_structure(self):
        """Test sentiment keywords are properly structured."""
        engine = EmotionalIntelligenceEngine()
        assert "positive" in engine.sentiment_keywords
        assert "negative" in engine.sentiment_keywords
        assert isinstance(engine.sentiment_keywords["positive"], list)

    def test_analyze_emotional_context(self):
        """Test emotional context analysis."""
        engine = EmotionalIntelligenceEngine()
        context = engine.analyze_emotional_context("I am so happy and excited!")
        assert isinstance(context, EmotionalContext)
        assert context.primary_emotion is not None
        assert context.intensity >= 0.0
        assert context.confidence >= 0.0


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
