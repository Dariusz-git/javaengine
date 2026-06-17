package com.physics3d.model;

/**
 * Classification of a celestial body. Used to drive rendering and
 * physics decisions that depend on the body's composition
 * (e.g. visual appearance, density-based effects in future work).
 *
 * Categories:
 *  - STAR         — the central body of a system (e.g. the Sun)
 *  - TERRESTRIAL  — small, dense, rocky planets (Mercury, Venus, Earth, Mars)
 *  - GAS_GIANT    — large planets composed mostly of hydrogen/helium (Jupiter, Saturn)
 *  - ICE_GIANT    — planets with significant volatile ices (Uranus, Neptune)
 */
public enum BodyType {
    STAR,
    TERRESTRIAL,
    GAS_GIANT,
    ICE_GIANT
}
