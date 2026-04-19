package edu.gmu.swe.knarr.bridge.fixture;

/**
 * Minimum non-trivial fixture: one field (so both transformers exercise
 * their field-wrapping / shadow-field logic), one arithmetic method
 * (tag-frame plumbing), one branch (so SPI hooks fire). Deliberately no
 * static init, no string ops, no arrays — those are tested
 * incrementally in follow-up fixtures once the simple case works.
 */
public final class SimpleCounter {
    private int count;

    public int addOne(int delta) {
        this.count = this.count + 1;
        if (delta < 0) {
            return this.count - 1;
        }
        return this.count + delta;
    }
}
