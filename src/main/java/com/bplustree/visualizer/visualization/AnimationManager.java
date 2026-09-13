package com.bplustree.visualizer.visualization;

import com.bplustree.visualizer.event.EventType;
import com.bplustree.visualizer.event.TreeAnimationEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.animation.PauseTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.util.Duration;

/** Drives animation events independently from tree mutation and rendering. */
public final class AnimationManager {
    public static final double DEFAULT_STEP_MILLIS = 650.0;
    public static final double FAST_STEP_MILLIS = 400.0;
    public static final double SLOW_STEP_MILLIS = 900.0;

    private final Consumer<TreeAnimationEvent> eventConsumer;
    private final ReadOnlyBooleanWrapper playing = new ReadOnlyBooleanWrapper(this, "playing", false);
    private final ReadOnlyIntegerWrapper currentIndex = new ReadOnlyIntegerWrapper(this, "currentIndex", -1);
    private final ReadOnlyIntegerWrapper eventCount = new ReadOnlyIntegerWrapper(this, "eventCount", 0);
    private final ReadOnlyBooleanWrapper atEnd = new ReadOnlyBooleanWrapper(this, "atEnd", true);
    private final DoubleProperty speedMultiplier = new SimpleDoubleProperty(this, "speedMultiplier", 1.0);

    private List<TreeAnimationEvent> events = List.of();
    private PauseTransition pendingStep;
    private long generation;

    public AnimationManager(Consumer<TreeAnimationEvent> eventConsumer) {
        this.eventConsumer = Objects.requireNonNull(eventConsumer, "eventConsumer");
        speedMultiplier.addListener((observable, oldValue, newValue) -> {
            double speed = newValue.doubleValue();
            if (!Double.isFinite(speed) || speed <= 0) {
                speedMultiplier.set(oldValue.doubleValue() > 0 ? oldValue.doubleValue() : 1.0);
            } else if (isPlaying()) {
                scheduleNext();
            }
        });
    }

    public AnimationManager(
            List<TreeAnimationEvent> events,
            Consumer<TreeAnimationEvent> eventConsumer) {
        this(eventConsumer);
        load(events);
    }

    public void load(List<TreeAnimationEvent> events) {
        Objects.requireNonNull(events, "events");
        stopPendingStep();
        this.events = List.copyOf(events);
        eventCount.set(this.events.size());
        setCurrentIndex(-1);
        playing.set(false);
    }

    public void play() {
        if (events.isEmpty() || isPlaying()) {
            return;
        }
        if (currentIndex.get() >= events.size() - 1) {
            setCurrentIndex(-1);
        }
        playing.set(true);
        if (currentIndex.get() < 0) {
            showIndex(0);
        }
        scheduleNext();
    }

    public void pause() {
        stopPendingStep();
        playing.set(false);
    }

    public void previous() {
        pause();
        if (events.isEmpty() || currentIndex.get() <= 0) {
            return;
        }
        showIndex(currentIndex.get() - 1);
    }

    public void next() {
        pause();
        if (events.isEmpty() || currentIndex.get() >= events.size() - 1) {
            return;
        }
        showIndex(currentIndex.get() + 1);
    }

    public void restart() {
        pause();
        setCurrentIndex(-1);
        if (!events.isEmpty()) {
            showIndex(0);
        }
    }

    /** Starts the current timeline again without emitting the first frame twice. */
    public void replay() {
        pause();
        setCurrentIndex(-1);
        play();
    }

    public void jumpTo(int index) {
        pause();
        if (events.isEmpty()) {
            return;
        }
        showIndex(Math.max(0, Math.min(events.size() - 1, index)));
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public ReadOnlyBooleanProperty playingProperty() {
        return playing.getReadOnlyProperty();
    }

    public int getCurrentIndex() {
        return currentIndex.get();
    }

    public ReadOnlyIntegerProperty currentIndexProperty() {
        return currentIndex.getReadOnlyProperty();
    }

    public int getEventCount() {
        return eventCount.get();
    }

    public ReadOnlyIntegerProperty eventCountProperty() {
        return eventCount.getReadOnlyProperty();
    }

    public boolean isAtEnd() {
        return atEnd.get();
    }

    public ReadOnlyBooleanProperty atEndProperty() {
        return atEnd.getReadOnlyProperty();
    }

    public DoubleProperty speedMultiplierProperty() {
        return speedMultiplier;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier.get();
    }

    public void setSpeedMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier <= 0) {
            throw new IllegalArgumentException("Speed multiplier must be finite and positive");
        }
        speedMultiplier.set(multiplier);
    }

    public List<TreeAnimationEvent> getEvents() {
        return events;
    }

    private void scheduleNext() {
        stopPendingStep();
        if (!isPlaying() || currentIndex.get() >= events.size() - 1) {
            playing.set(false);
            return;
        }

        TreeAnimationEvent nextEvent = events.get(currentIndex.get() + 1);
        double stepDuration = durationFor(nextEvent.type());
        long expectedGeneration = generation;
        PauseTransition transition = new PauseTransition(
                Duration.millis(stepDuration / speedMultiplier.get()));
        pendingStep = transition;
        transition.setOnFinished(event -> {
            if (transition != pendingStep || expectedGeneration != generation || !isPlaying()) {
                return;
            }
            pendingStep = null;
            showIndex(currentIndex.get() + 1);
            scheduleNext();
        });
        transition.play();
    }

    private static double durationFor(EventType type) {
        return switch (type) {
            case VISIT_NODE, COMPARE_KEY, TRAVERSE_EDGE, MATCH_KEY -> FAST_STEP_MILLIS;
            case NODE_OVERFLOW, NODE_UNDERFLOW, SPLIT_NODE, MERGE_NODE, BORROW_KEY,
                    PROMOTE_KEY, CREATE_ROOT, SHRINK_ROOT -> SLOW_STEP_MILLIS;
            case INSERT_KEY, DELETE_KEY, UPDATE_SEPARATOR, HIGHLIGHT_RANGE, COMPLETE, ERROR ->
                    DEFAULT_STEP_MILLIS;
        };
    }

    private void showIndex(int index) {
        setCurrentIndex(index);
        eventConsumer.accept(events.get(index));
    }

    private void setCurrentIndex(int index) {
        atEnd.set(events.isEmpty() || index == events.size() - 1);
        currentIndex.set(index);
    }

    private void stopPendingStep() {
        generation++;
        if (pendingStep != null) {
            pendingStep.stop();
            pendingStep = null;
        }
    }
}
