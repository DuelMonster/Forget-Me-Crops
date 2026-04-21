package com.fastharvester.enums;

/**
 * DurabilityMode: The mood ring for your hoe's lifespan!
 * <p>
 * This enum lets you pick how tough your hoes should be. Will they last forever, or crumble at the first sign of work?
 * </p>
 * <p>
 * Why does this matter? Because every farmer has a different philosophy on tool suffering.
 * </p>
 */
public enum DurabilityMode {
    /**
     * The classic: Unbreaking and Mending work as expected. (Boring, but reliable!)
     */
    NORMAL,
    /**
     * Ignore Unbreaking—hoes take damage like they're in a rock band.
     */
    IGNORE_UNBREAKING,
    /**
     * No durability loss. Your hoe is immortal. (But is it happy?)
     */
    NONE
}
