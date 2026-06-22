package com.physics3d.graphics;

import com.physics3d.engine.PhysicsEngine;
import com.physics3d.model.CelestialBody;
import com.physics3d.model.OrbitTrail;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;


import java.awt.Font;
import java.nio.FloatBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Handles rendering of the 3D scene using OpenGL (fixed-function GL11).
 * Features an orbital camera:
 *   - Left mouse drag: rotate around the look-at target (azimuth / elevation)
 *   - Scroll wheel:    zoom in / out (camera distance)
 *   - WASD keys:       pan the look-at target in the view plane
 *   - T key: toggle orbits
 * World coordinates are astronomical (~1e11 m). Those magnitudes lose precision
 * in float GL matrices, so the scene is uniformly scaled down by WORLD_SCALE
 * before being submitted to OpenGL.
 */
public class Renderer {
    private long window;
    private int width;
    private int height;
    private boolean shouldClose;

    // Scale factor applied to world coordinates before rendering (meters -> scene units)
    private static final float WORLD_SCALE = 1e-9f;
    // Sun position in world space (updated each frame from the body list)
    private Vector3f sunPosition = new Vector3f(0, 0, 0);
    // Lighting parameters
    private static final float[] AMBIENT_COLOR = {0.08f, 0.08f, 0.12f, 1.0f};
    private static final float[] SUN_DIFFUSE = {1.0f, 0.95f, 0.85f, 1.0f};
    private static final float[] SUN_SPECULAR = {1.0f, 1.0f, 0.9f, 1.0f};
    private static final float SHININESS = 32.0f;
    // Sphere tessellation (higher = smoother lighting)
    private static final int SPHERE_STACKS = 24;
    private static final int SPHERE_SLICES = 32;

    // Starfield: pre-generated random star positions (in scene units, far away)
    private static final int STAR_COUNT = 1500;
    private final float[] starPositions = new float[STAR_COUNT * 3];
    private final float[] starColors = new float[STAR_COUNT * 3];
    private boolean starsInitialized = false;

    private boolean showTrails = true; // Toggle with T key

    // TEMPORARY: blue Keplerian theoretical orbit is disabled while we work on
    // a full orbit implementation using live ESA data. Flip to true to re-enable.
    private boolean showTheoreticalOrbit = false;

    // Dynamic orbit fade: oldest trail point (1 simulation year old) is fully transparent,
    // newest point is fully opaque. Linear fade.
    private static final double TRAIL_FADE_YEARS = 1.0;
    // Current simulation time (in years), updated each frame for trail fade calculations.
    private double currentSimTimeYears = 0.0;

    // ---- HUD / planet selection ----
    private TextRenderer textRenderer;
    private int selectedIndex = -1; // -1 = no selection; otherwise index into the body list
    private boolean tabPressed = false; // edge-detect for Tab key
    private boolean tabShift = false;   // true when Tab is pressed together with Shift
    private boolean trackingEnabled = false; // true when camera follows the selected body

    // ---- Time speed control ----
    private PhysicsEngine physicsEngine;
    private boolean sliderDragging = false; // true when user is dragging the slider handle

    // ---- Earth date/time (captured at startup, advanced by simulation time) ----
    private final LocalDateTime earthStartDateTime = LocalDateTime.now();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy:MM:dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mma");

    // ---- Orbit camera state ----
    private float camDistance = 800.0f;     // distance from target, in scene units
    private float camAzimuth = 0.0f;        // radians, rotation around Y
    private float camElevation = 0.6f;      // radians, up/down
    private float targetX = 0.0f;
    private float targetY = 0.0f;
    private float targetZ = 0.0f;

    // Mouse drag tracking
    private boolean dragging = false;
    private double lastMouseX;
    private double lastMouseY;

    // ---- HUD inline editing ----
    // When editingField is non-null, the user is typing into the corresponding
    // numeric field of the currently selected body. editBuffer holds the raw
    // text being typed. The field rectangles (x, y, w, h) are recomputed each
    // frame inside renderHud() so click hit-testing stays in sync with the
    // current window size.
    private enum EditField { MASS, RADIUS, ECCENTRICITY, SEMI_MAJOR_AXIS }
    private EditField editingField = null;
    private final StringBuilder editBuffer = new StringBuilder();
    // Original value (as text) captured when editing starts, so Escape can restore it.
    private String editOriginalText = "";
    // Cached rectangles for the four editable fields, in screen coords (y from bottom).
    // Index matches EditField.ordinal().
    private final float[][] editFieldRects = new float[EditField.values().length][4];
    private boolean editRectsValid = false;
    // Cursor blink timer (seconds since last toggle).
    private double editCursorBlinkTimer = 0.0;
    private boolean editCursorVisible = true;
    /**
     * True once the user has typed at least one character into the edit
     * buffer. While false, the next printable character clears the seeded
     * value first (so typing "31" replaces "1.989e+30" instead of producing
     * the invalid "1.989e+3031").
     */
    private boolean editBufferDirty = false;

    /**
     * Reference values captured the first time a body is rendered. Used to
     * display editable fields as multipliers (current / reference) so the
     * user can type e.g. "2" to make a planet twice as heavy instead of
     * having to memorise its mass in kilograms. Keyed by body name.
     */
    private final Map<String, Float> referenceMass = new HashMap<>();
    private final Map<String, Float> referenceRadius = new HashMap<>();
    private final Map<String, Double> referenceSemiMajorAxis = new HashMap<>();

    private final Matrix4f viewMatrix = new Matrix4f();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    public Renderer(String title, int width, int height) {
        this.width = width;
        this.height = height;
        this.shouldClose = false;

        initGLFW();
        createWindow(title);
        initOpenGL();
        initStarfield();
    }

    /** Set the physics engine reference for time speed control. */
    public void setPhysicsEngine(PhysicsEngine engine) {
        this.physicsEngine = engine;
    }

    private void initGLFW() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
    }

    private void createWindow(String title) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(width, height, title, 0, 0);
        if (window == 0) {
            throw new RuntimeException("Failed to create the GLFW window");
        }

        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_RELEASE) {
                if (editingField != null) {
                    // Escape while editing: cancel and restore original value.
                    cancelEditing();
                } else {
                    shouldClose = true;
                }
            }

            // While editing, route printable keys into the edit buffer instead
            // of triggering global shortcuts.
            if (editingField != null && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                if (handleEditingKey(key, mods)) {
                    return;
                }
            }

            if (key == GLFW.GLFW_KEY_T && action == GLFW.GLFW_RELEASE) {
                showTrails = !showTrails;
            }

            // Tab cycles through celestial bodies for the HUD.
            // Tab + Shift cycles in the opposite direction.
            if (key == GLFW.GLFW_KEY_TAB && action == GLFW.GLFW_PRESS) {
                tabPressed = true;
                tabShift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
            }

            // Time speed controls: UP arrow = faster, DOWN arrow = slower
            if (physicsEngine != null) {
                if (key == GLFW.GLFW_KEY_UP && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    physicsEngine.multiplyTimeScale(1.5);
                }
                if (key == GLFW.GLFW_KEY_DOWN && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    physicsEngine.multiplyTimeScale(1.0 / 1.5);
                }
                // R key: reset time scale to default
                if (key == GLFW.GLFW_KEY_R && action == GLFW.GLFW_RELEASE) {
                    physicsEngine.setTimeScale(physicsEngine.getDefaultTimeScale());
                }
                // Space: pause/resume
                if (key == GLFW.GLFW_KEY_SPACE && action == GLFW.GLFW_RELEASE) {
                    if (physicsEngine.getTimeScale() > 0) {
                        physicsEngine.setTimeScale(0);
                    } else {
                        physicsEngine.setTimeScale(physicsEngine.getDefaultTimeScale());
                    }
                }
            }
        });



        GLFW.glfwSetWindowCloseCallback(window, w -> shouldClose = true);

        // Track window size so the projection aspect ratio stays correct
        GLFW.glfwSetFramebufferSizeCallback(window, (w, fbWidth, fbHeight) -> {
            this.width = Math.max(1, fbWidth);
            this.height = Math.max(1, fbHeight);
        });

        // Mouse button: start/stop dragging to rotate the camera, or slider,
        // or activate a HUD value field for inline editing.
        GLFW.glfwSetMouseButtonCallback(window, (w, button, action, mods) -> {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW.GLFW_PRESS) {
                    double[] mx = new double[1];
                    double[] my = new double[1];
                    GLFW.glfwGetCursorPos(window, mx, my);

                    // Check if click is on the slider area first
                    if (isClickOnSlider(mx[0], my[0])) {
                        sliderDragging = true;
                        updateTimeScaleFromMouse(mx[0]);
                    } else if (handleHudFieldClick(mx[0], my[0])) {
                        // Click landed on an editable HUD value field; editing
                        // is now active. Don't start camera drag.
                    } else {
                        dragging = true;
                        lastMouseX = mx[0];
                        lastMouseY = my[0];
                    }
                } else if (action == GLFW.GLFW_RELEASE) {
                    dragging = false;
                    sliderDragging = false;
                }
            }
        });

        // Cursor movement: rotate camera while dragging, or update slider
        GLFW.glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (sliderDragging) {
                updateTimeScaleFromMouse(xpos);
            } else if (dragging) {
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;
                lastMouseX = xpos;
                lastMouseY = ypos;

                camAzimuth += (float) dx * 0.005f;
                camElevation += (float) dy * 0.005f;

                // Clamp elevation to avoid flipping over the poles
                float limit = (float) (Math.PI / 2.0 - 0.05);
                if (camElevation > limit) camElevation = limit;
                if (camElevation < -limit) camElevation = -limit;
            }
        });

        // Scroll: zoom in / out
        GLFW.glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            float factor = (float) Math.pow(1.1, -yoffset);
            camDistance *= factor;
            // Lower bound matches the auto-fit minimum (Mercury: drawRadius 0.4 * 3 = 1.2,
            // clamped up to 2.0).  This lets the user zoom in close enough to inspect
            // the smallest body without clipping through its surface.
            if (camDistance < 2.0f) camDistance = 2.0f;
            if (camDistance > 100000.0f) camDistance = 100000.0f;
        });

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
    }

    private void initOpenGL() {
        GL.createCapabilities();
        GL11.glClearColor(0.0f, 0.0f, 0.02f, 1.0f);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);
        GL11.glFrontFace(GL11.GL_CCW);

        // Enable per-vertex lighting with proper normals
        GL11.glEnable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_LIGHT0);
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glColorMaterial(GL11.GL_FRONT_AND_BACK, GL11.GL_AMBIENT_AND_DIFFUSE);

        // Set up light 0 (the Sun) - position will be updated each frame
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_AMBIENT, AMBIENT_COLOR);
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, SUN_DIFFUSE);
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_SPECULAR, SUN_SPECULAR);

        // Specular material settings (per-body via glMaterialfv)
        float[] matSpec = {1.0f, 1.0f, 1.0f, 1.0f};
        GL11.glMaterialfv(GL11.GL_FRONT_AND_BACK, GL11.GL_SPECULAR, matSpec);
        GL11.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, SHININESS);

        // Normalize normals so lighting is correct regardless of sphere scale
        GL11.glEnable(GL11.GL_NORMALIZE);

        // Initialize starfield
        initStars();
    }

    /** Generate random star positions on a large sphere around the scene. */
    private void initStars() {
        if (starsInitialized) return;
        java.util.Random rng = new java.util.Random(42L); // fixed seed for reproducibility
        float starRadius = 200000.0f; // far away in scene units
        for (int i = 0; i < STAR_COUNT; i++) {
            // Uniform sphere distribution
            double u = rng.nextDouble();
            double v = rng.nextDouble();
            double theta = 2.0 * Math.PI * u;
            double phi = Math.acos(2.0 * v - 1.0);
            float x = (float) (starRadius * Math.sin(phi) * Math.cos(theta));
            float y = (float) (starRadius * Math.cos(phi));
            float z = (float) (starRadius * Math.sin(phi) * Math.sin(theta));
            starPositions[i * 3] = x;
            starPositions[i * 3 + 1] = y;
            starPositions[i * 3 + 2] = z;
            // Vary star color slightly (white to pale yellow/blue)
            float brightness = 0.5f + rng.nextFloat() * 0.5f;
            float colorVar = rng.nextFloat();
            if (colorVar < 0.7f) {
                // White-ish
                starColors[i * 3] = brightness;
                starColors[i * 3 + 1] = brightness;
                starColors[i * 3 + 2] = brightness;
            } else if (colorVar < 0.85f) {
                // Pale yellow
                starColors[i * 3] = brightness;
                starColors[i * 3 + 1] = brightness * 0.95f;
                starColors[i * 3 + 2] = brightness * 0.7f;
            } else {
                // Pale blue
                starColors[i * 3] = brightness * 0.7f;
                starColors[i * 3 + 1] = brightness * 0.85f;
                starColors[i * 3 + 2] = brightness;
            }
        }
        starsInitialized = true;
    }

    /** Handle keyboard panning of the camera target. Called once per frame. */
    private void processInput() {
        // Any WASD press disables planet tracking and returns to free camera control.
        if (trackingEnabled) {
            if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
                trackingEnabled = false;
            }
        }

        float panSpeed = camDistance * 0.02f;

        // Camera forward direction projected onto the XZ plane
        float fx = (float) (Math.cos(camElevation) * Math.sin(camAzimuth));
        float fz = (float) (Math.cos(camElevation) * Math.cos(camAzimuth));
        // Right vector (perpendicular to forward on XZ plane)
        float rx = (float) Math.sin(camAzimuth - Math.PI / 2.0);
        float rz = (float) Math.cos(camAzimuth - Math.PI / 2.0);

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            targetX += fx * panSpeed;
            targetZ += fz * panSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            targetX -= fx * panSpeed;
            targetZ -= fz * panSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            targetX -= rx * panSpeed;
            targetZ -= rz * panSpeed;
        }
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            targetX += rx * panSpeed;
            targetZ += rz * panSpeed;
        }
    }

    private void setupProjection() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();

        float aspect = (float) width / (float) height;
        float near = 1.0f;
        float far = 500000.0f;
        // 60-degree vertical field of view
        float fovY = (float) Math.toRadians(60.0);
        float top = (float) (Math.tan(fovY / 2.0) * near);
        float bottom = -top;
        float right = top * aspect;
        float left = -right;
        GL11.glFrustum(left, right, bottom, top, near, far);
    }

    private void setupCamera() {
        // Camera position on a sphere around the target
        float cx = targetX + (float) (camDistance * Math.cos(camElevation) * Math.sin(camAzimuth));
        float cy = targetY + (float) (camDistance * Math.sin(camElevation));
        float cz = targetZ + (float) (camDistance * Math.cos(camElevation) * Math.cos(camAzimuth));

        viewMatrix.identity().lookAt(
            cx, cy, cz,
            targetX, targetY, targetZ,
            0.0f, 1.0f, 0.0f
        );

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        viewMatrix.get(matrixBuffer);
        GL11.glLoadMatrixf(matrixBuffer);
    }

    /** Convert float array to FloatBuffer and reset position. */
    private FloatBuffer toFloatBuffer(FloatBuffer buffer, float[] array) {
        buffer.clear();
        buffer.put(array);
        buffer.flip();
        return buffer;
    }



    public void render(List<CelestialBody> bodies) {
        processInput();

        // Blink the text-edit cursor at ~2 Hz while a field is being edited.
        if (editingField != null) {
            editCursorBlinkTimer += 1.0 / 60.0;
            if (editCursorBlinkTimer >= 0.5) {
                editCursorBlinkTimer = 0.0;
                editCursorVisible = !editCursorVisible;
            }
        } else {
            editCursorVisible = true;
        }

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        setupProjection();

        // Update current simulation time (in years) for trail fade calculations
        currentSimTimeYears = bodies.isEmpty() ? 0.0
                : bodies.get(0).getTrail().getPositions().stream()
                        .mapToDouble(p -> p.timeYears)
                        .max().orElse(0.0);

        // If tracking is enabled and a body is selected, follow its current position.
        if (trackingEnabled && selectedIndex >= 0 && selectedIndex < bodies.size()) {
            Vector3f trackedPos = bodies.get(selectedIndex).getPosition();
            targetX = trackedPos.x * WORLD_SCALE;
            targetY = trackedPos.y * WORLD_SCALE;
            targetZ = trackedPos.z * WORLD_SCALE;
        }

        setupCamera();

        // Find sun position first
        for (CelestialBody body : bodies) {
            if ("Sun".equals(body.getName())) {
                Vector3f pos = body.getPosition();
                sunPosition.set(pos.x * WORLD_SCALE, pos.y * WORLD_SCALE, pos.z * WORLD_SCALE);
                break;
            }
        }

        // ---- Draw starfield background (no lighting, no depth write) ----
        drawStarfield();

        // ---- Draw theoretical orbits (ideal ellipses) ----
        // TEMPORARY: disabled while we work on a full orbit implementation using live ESA data.
        if (showTheoreticalOrbit) {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor3f(0.3f, 0.3f, 0.5f);
            for (CelestialBody body : bodies) {
                Queue<Vector3f> theoretical = body.getTrail().getTheoreticalOrbit();
                if (theoretical.size() > 1) {
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                    for (Vector3f pos : theoretical) {
                        GL11.glVertex3f(pos.x * WORLD_SCALE, pos.y * WORLD_SCALE, pos.z * WORLD_SCALE);
                    }
                    GL11.glEnd();
                }
            }
        }

        // ---- Draw actual trajectory (from simulation) ----
        // White color, with linear alpha fade based on age (oldest = transparent, newest = opaque)
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        for (CelestialBody body : bodies) {
            Queue<OrbitTrail.TrailPoint> actual = body.getTrail().getPositions();
            if (actual.size() > 1) {
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (OrbitTrail.TrailPoint point : actual) {
                    float alpha = (float) Math.max(0.0, Math.min(1.0,
                            1.0 - (currentSimTimeYears - point.timeYears) / TRAIL_FADE_YEARS));
                    GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
                    GL11.glVertex3f(point.position.x * WORLD_SCALE,
                                    point.position.y * WORLD_SCALE,
                                    point.position.z * WORLD_SCALE);
                }
                GL11.glEnd();
            }
        }
        GL11.glDisable(GL11.GL_BLEND);

        // ---- Draw orbital trails ----
        if (showTrails) {
            GL11.glLineWidth(1.0f);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            for (CelestialBody body : bodies) {
                // TEMPORARY: blue Keplerian theoretical orbit disabled while we work on
                // a full orbit implementation using live ESA data.
                if (showTheoreticalOrbit) {
                    Queue<Vector3f> theoretical = body.getTrail().getTheoreticalOrbit();
                    if (theoretical.size() > 1) {
                        GL11.glColor3f(0.3f, 0.3f, 0.6f);
                        GL11.glBegin(GL11.GL_LINE_STRIP);
                        Vector3f[] theoryArray = theoretical.toArray(new Vector3f[0]);
                        for (Vector3f pos : theoryArray) {
                            GL11.glVertex3f(pos.x * WORLD_SCALE, pos.y * WORLD_SCALE, pos.z * WORLD_SCALE);
                        }
                        if (theoryArray.length > 0) {
                            GL11.glVertex3f(theoryArray[0].x * WORLD_SCALE, theoryArray[0].y * WORLD_SCALE, theoryArray[0].z * WORLD_SCALE);
                        }
                        GL11.glEnd();
                    }
                }

                Queue<OrbitTrail.TrailPoint> positions = body.getTrail().getPositions();
                if (positions.size() > 1) {
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                    OrbitTrail.TrailPoint[] posArray = positions.toArray(new OrbitTrail.TrailPoint[0]);
                    for (OrbitTrail.TrailPoint point : posArray) {
                        float alpha = (float) Math.max(0.0, Math.min(1.0,
                                1.0 - (currentSimTimeYears - point.timeYears) / TRAIL_FADE_YEARS));
                        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
                        GL11.glVertex3f(point.position.x * WORLD_SCALE,
                                        point.position.y * WORLD_SCALE,
                                        point.position.z * WORLD_SCALE);
                    }
                    GL11.glEnd();
                    GL11.glDisable(GL11.GL_BLEND);
                }
            }

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glLineWidth(1.0f);
        }

        // ---- Draw celestial bodies with proper lighting ----
        GL11.glEnable(GL11.GL_LIGHTING);
        // Update light 0 position to the Sun (in world space)
        float[] lightPos = {sunPosition.x, sunPosition.y, sunPosition.z, 1.0f};
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, lightPos);

        for (CelestialBody body : bodies) {
            Vector3f pos = body.getPosition();
            float x = pos.x * WORLD_SCALE;
            float y = pos.y * WORLD_SCALE;
            float z = pos.z * WORLD_SCALE;
            float r = body.getDrawRadius();

            float[] baseColor = colorFor(body.getName());

            if ("Sun".equals(body.getName())) {
                // Sun: emit light, no shading. Disable lighting for the Sun itself.
                GL11.glDisable(GL11.GL_LIGHTING);
                int sunTex = body.getTextureId();
                if (sunTex != -1) {
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, sunTex);
                    // Warm tint so the sun texture looks luminous
                    GL11.glColor3f(1.0f, 0.95f, 0.85f);
                } else {
                    GL11.glColor3f(baseColor[0], baseColor[1], baseColor[2]);
                }
                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, z);
                applyAxialRotation(body);
                drawSphere(r, SPHERE_STACKS, SPHERE_SLICES);
                GL11.glPopMatrix();
                if (sunTex != -1) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                }
                GL11.glEnable(GL11.GL_LIGHTING);
            } else {
                // Planets: use OpenGL lighting with per-vertex normals
                // Set the base color (diffuse + ambient via COLOR_MATERIAL)
                GL11.glColor3f(baseColor[0], baseColor[1], baseColor[2]);
                // Set specular intensity per body (gas giants get less specular)
                float[] spec = specularFor(body.getName());
                GL11.glMaterialfv(GL11.GL_FRONT_AND_BACK, GL11.GL_SPECULAR, spec);
                GL11.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, shininessFor(body.getName()));

                // Bind texture if this body has one. The texture modulates the
                // lit color via GL_MODULATE, so the per-body tint still applies.
                int texId = body.getTextureId();
                boolean textured = texId != -1;
                if (textured) {
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                }

                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, z);
                applyAxialRotation(body);
                drawSphere(r, SPHERE_STACKS, SPHERE_SLICES);
                GL11.glPopMatrix();

                if (textured) {
                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                }

                // ---- Earth multi-texture: night lights + cloud overlay ----
                int nightTex = body.getNightTextureId();
                int cloudTex = body.getCloudTextureId();
                if (nightTex != -1 || cloudTex != -1) {
                    // Compute sun direction relative to this body (in world space)
                    Vector3f sunDir = new Vector3f(sunPosition).sub(pos).normalize();

                    // --- Night-lights pass (additive blend, dark side only) ---
                    if (nightTex != -1) {
                        GL11.glDepthMask(false);          // don't write depth
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE); // additive
                        GL11.glDisable(GL11.GL_LIGHTING);
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, nightTex);
                        GL11.glColor3f(1, 1, 1);         // full brightness; texture modulates

                        GL11.glPushMatrix();
                        GL11.glTranslatef(x, y, z);
                        applyAxialRotation(body);
                        drawSphereNight(r, SPHERE_STACKS, SPHERE_SLICES, sunDir);
                        GL11.glPopMatrix();

                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        GL11.glDisable(GL11.GL_BLEND);
                        GL11.glDepthMask(true);
                        GL11.glEnable(GL11.GL_LIGHTING);
                    }

                    // --- Cloud overlay pass (alpha blend, fully lit) ---
                    if (cloudTex != -1) {
                        GL11.glDepthMask(false);
                        GL11.glEnable(GL11.GL_BLEND);
                        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, cloudTex);
                        GL11.glColor4f(1, 1, 1, 0.55f);  // semi-transparent clouds

                        GL11.glPushMatrix();
                        GL11.glTranslatef(x, y, z);
                        applyAxialRotation(body);
                        // Slightly larger radius so clouds sit above the surface
                        drawSphere(r * 1.005f, SPHERE_STACKS, SPHERE_SLICES);
                        GL11.glPopMatrix();

                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                        GL11.glDisable(GL11.GL_BLEND);
                        GL11.glDepthMask(true);
                    }
                }

                // ---- Saturn rings (alpha-blended ring overlay) ----
                // Rings are tied to Saturn's axial tilt (so they stay aligned
                // with the planet) but they do NOT spin with the body — the
                // ring texture is drawn without applying the rotation angle.
                int ringTex = body.getRingTextureId();
                if (ringTex != -1) {
                    GL11.glDepthMask(false);
                    GL11.glEnable(GL11.GL_BLEND);
                    GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GL11.glDisable(GL11.GL_LIGHTING);
                    GL11.glEnable(GL11.GL_TEXTURE_2D);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, ringTex);
                    GL11.glColor4f(1, 1, 1, 1.0f);

                    GL11.glPushMatrix();
                    GL11.glTranslatef(x, y, z);
                    // Apply ONLY the axial tilt — not the spin angle — so the
                    // rings stay aligned with Saturn's equatorial plane.
                    applyRingTilt(body);
                    // Inner radius slightly above Saturn's surface, outer
                    // radius ~2.3× the planet radius (matches real proportions).
                    drawRing(r * 1.2f, r * 2.3f, 96);
                    GL11.glPopMatrix();

                    GL11.glDisable(GL11.GL_TEXTURE_2D);
                    GL11.glDisable(GL11.GL_BLEND);
                    GL11.glDepthMask(true);
                    GL11.glEnable(GL11.GL_LIGHTING);
                }
            }
        }

        GL11.glDisable(GL11.GL_LIGHTING);

        // ---- HUD overlay (drawn last, on top of the 3D scene) ----
        renderHud(bodies);

        GLFW.glfwSwapBuffers(window);
        GLFW.glfwPollEvents();
    }

    /** Draw a starfield of randomly placed points on a large sphere. */
    private void drawStarfield() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glPointSize(1.5f);

        GL11.glBegin(GL11.GL_POINTS);
        for (int i = 0; i < STAR_COUNT; i++) {
            // Vary brightness slightly per star
            float brightness = 0.6f + (i % 5) * 0.08f;
            GL11.glColor3f(
                starColors[i * 3] * brightness,
                starColors[i * 3 + 1] * brightness,
                starColors[i * 3 + 2] * brightness
            );
            GL11.glVertex3f(starPositions[i * 3], starPositions[i * 3 + 1], starPositions[i * 3 + 2]);
        }
        GL11.glEnd();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    
    /**
     * Lazy-initialise the bitmap font renderer. Done lazily because we need
     * a current GL context, which only exists after the window is created.
     */
    private TextRenderer getTextRenderer() {
        if (textRenderer == null) {
            textRenderer = new TextRenderer(new Font("SansSerif", Font.PLAIN, 16));
        }
        return textRenderer;
    }

    /**
     * Draw the HUD overlay: a body list on the left, and detailed info for
     * the currently selected body on the right.
     */
    private void renderHud(List<CelestialBody> bodies) {
        TextRenderer tr = getTextRenderer();

        // Handle Tab press: cycle to the next body (or wrap) and enable planet tracking.
        // Tab + Shift cycles in the opposite direction.
        if (tabPressed) {
            tabPressed = false;
            boolean reverse = tabShift;
            tabShift = false;
            if (bodies.isEmpty()) {
                selectedIndex = -1;
                trackingEnabled = false;
            } else {
                int n = bodies.size();
                if (reverse) {
                    selectedIndex = ((selectedIndex - 1) % n + n) % n;
                } else {
                    selectedIndex = (selectedIndex + 1) % n;
                }
                trackingEnabled = true;

                // Auto-fit zoom: pick a camera distance proportional to the
                // body's drawn radius so it fills a comfortable portion of
                // the viewport.  drawRadius is in scene units, so multiplying
                // by 3 gives a nice "planet fills ~1/3 of the screen" framing.
                // Small bodies get a minimum distance so they don't disappear
                // into the depth buffer; large bodies (Sun) get a generous
                // distance so the corona/halo doesn't dominate the view.
                CelestialBody picked = bodies.get(selectedIndex);
                float pickedRadius = picked.getDrawRadius();
                if (pickedRadius <= 0.0f) {
                    // Fallback for bodies without an explicit draw radius
                    pickedRadius = picked.getRadius() * WORLD_SCALE;
                }
                float fitDistance = Math.max(pickedRadius * 3.0f, 2.0f);
                camDistance = fitDistance;
            }
        }

        tr.beginFrame(width, height);

        // --- Top-left: title + controls hint ---
        float[] titleColor = {1.0f, 1.0f, 1.0f, 1.0f};
        float[] hintColor  = {0.7f, 0.7f, 0.7f, 1.0f};
        float[] bgColor    = {0.0f, 0.0f, 0.0f, 0.55f};

        int padding = 6;
        int lineHeight = tr.getCharHeight() + 2;
        int left = 10;
        int top = height - 10;

        tr.drawStringWithBackground("Solar System Simulator", left, top - lineHeight, titleColor, bgColor, padding);
        tr.drawStringWithBackground("TAB: select body", left, top - 2 * lineHeight, hintColor, bgColor, padding);
        tr.drawStringWithBackground("T: toggle trails", left, top - 3 * lineHeight, hintColor, bgColor, padding);
        tr.drawStringWithBackground("WASD: pan, drag: rotate, scroll: zoom", left, top - 4 * lineHeight, hintColor, bgColor, padding);

        // --- Left side: body list ---
        int listTop = top - 5 * lineHeight - 10;
        tr.drawStringWithBackground("Bodies:", left, listTop - lineHeight, titleColor, bgColor, padding);
        for (int i = 0; i < bodies.size(); i++) {
            CelestialBody body = bodies.get(i);
            String marker = (i == selectedIndex) ? "> " : "  ";
            String label = marker + body.getName();
            float[] color = (i == selectedIndex)
                    ? new float[]{1.0f, 1.0f, 0.3f, 1.0f}
                    : new float[]{0.9f, 0.9f, 0.9f, 1.0f};
            tr.drawStringWithBackground(label, left, listTop - (i + 2) * lineHeight, color, bgColor, padding);
        }

        // --- Right side: selected body details ---
        if (selectedIndex >= 0 && selectedIndex < bodies.size()) {
            CelestialBody body = bodies.get(selectedIndex);
            // Capture reference values the first time we see each body so the
            // HUD can display editable fields as multipliers (current / reference).
            // This lets the user type e.g. "2" to double a planet's mass instead
            // of having to type its mass in kilograms.
            String bodyName = body.getName();
            referenceMass.computeIfAbsent(bodyName, k -> body.getMass());
            referenceRadius.computeIfAbsent(bodyName, k -> body.getRadius());
            referenceSemiMajorAxis.computeIfAbsent(bodyName, k -> body.getSemiMajorAxis());

            float speed = body.getVelocity().length();
            float mass = body.getMass();
            float radius = body.getRadius();
            double distanceFromSun = 0.0;
            for (CelestialBody other : bodies) {
                if ("Sun".equals(other.getName())) {
                    distanceFromSun = body.getPosition().distance(other.getPosition());
                    break;
                }
            }

            // Lines and their associated editable fields (null = not editable).
            // Order matches the EditField enum: MASS, RADIUS, ECCENTRICITY, SEMI_MAJOR_AXIS.
            // Editable fields are shown as multipliers relative to the body's
            // initial reference value (captured above), so the user can type
            // e.g. "2" to double a planet's mass instead of typing kilograms.
            float refMass = referenceMass.getOrDefault(bodyName, mass);
            float refRadius = referenceRadius.getOrDefault(bodyName, radius);
            double refA = referenceSemiMajorAxis.getOrDefault(bodyName, body.getSemiMajorAxis());
            float massMult = (refMass != 0f) ? mass / refMass : 1f;
            float radiusMult = (refRadius != 0f) ? radius / refRadius : 1f;
            double aMult = (refA != 0.0) ? body.getSemiMajorAxis() / refA : 1.0;

            String[] lines = {
                    "Selected: " + body.getName(),
                    String.format("Velocity: %.3e m/s", speed),
                    String.format("Mass:     %.3fx", massMult),
                    String.format("Radius:   %.3fx", radiusMult),
                    String.format("Dist Sun: %.3e m", distanceFromSun),
                    String.format("Eccentr.: %.4f", body.getEccentricity()),
                    String.format("SemiMaj.: %.3fx", aMult)
            };
            EditField[] fieldForLine = {
                    null,                       // Selected
                    null,                       // Velocity
                    EditField.MASS,             // Mass
                    EditField.RADIUS,           // Radius
                    null,                       // Dist Sun
                    EditField.ECCENTRICITY,     // Eccentricity
                    EditField.SEMI_MAJOR_AXIS   // SemiMajorAxis
            };

            int panelWidth = 320;
            int right = width - panelWidth - 10;
            int panelTop = top;

            // Reset rect cache; will be repopulated below.
            editRectsValid = false;

            for (int i = 0; i < lines.length; i++) {
                float[] color = (i == 0) ? titleColor : new float[]{0.95f, 0.95f, 0.95f, 1.0f};
                int y = panelTop - (i + 1) * lineHeight;

                // If this line is currently being edited, swap in the live
                // edit buffer text so the user sees what they're typing.
                String displayText = lines[i];
                if (editingField != null && fieldForLine[i] == editingField) {
                    displayText = lines[i].split(":")[0] + ": " + editBuffer.toString();
                    color = new float[]{1.0f, 1.0f, 0.3f, 1.0f};
                }

                tr.drawStringWithBackground(displayText, right, y, color, bgColor, padding);

                // Track the screen-space rectangle of each editable field so
                // mouse clicks can be matched against them.
                if (fieldForLine[i] != null) {
                    int idx = fieldForLine[i].ordinal();
                    int textW = tr.stringWidth(displayText);
                    int textH = tr.getCharHeight();
                    // Y is already in y-from-bottom coordinates (panelTop = height - 10).
                    editFieldRects[idx][0] = right - padding;
                    editFieldRects[idx][1] = y - padding;
                    editFieldRects[idx][2] = right - padding + textW + 2 * padding;
                    editFieldRects[idx][3] = y - padding + textH + 2 * padding;
                }
            }
            editRectsValid = true;

            // Draw a border around the active editing field (raw GL, since
            // TextRenderer doesn't expose a border primitive).
            // IMPORTANT: stay inside the beginFrame/endFrame block so the
            // orthographic projection remains active. Drawing raw GL here
            // works because beginFrame() already set up MODELVIEW as identity
            // and disabled depth test.
            if (editingField != null) {
                int idx = editingField.ordinal();
                float[] r = editFieldRects[idx];
                float x0 = r[0];
                float y0 = r[1];
                float x1 = r[2];
                float y1 = r[3];
                drawEditHighlight(x0, y0, x1, y1);
                drawEditBorder(x0, y0, x1, y1);
                // Blinking cursor at the end of the edit buffer.
                if (editCursorVisible) {
                    String prefix = lines[idxForField(editingField)].split(":")[0] + ": ";
                    int prefixW = tr.stringWidth(prefix);
                    int bufferW = tr.stringWidth(editBuffer.toString());
                    int cursorX = (int) (right - padding + prefixW + bufferW);
                    int cursorY = panelTop - (idxForField(editingField) + 1) * lineHeight;
                    drawEditCursor(cursorX, cursorY, tr.getCharHeight());
                }
            }
        } else {
            editRectsValid = false;
        }

        // --- Bottom-left: Earth date & time (advanced by simulation time) ---
        if (physicsEngine != null) {
            long elapsedSec = (long) physicsEngine.getSimElapsedSeconds();
            LocalDateTime simNow = earthStartDateTime.plusSeconds(elapsedSec);
            String dateStr = simNow.format(DATE_FMT);
            String timeStr = simNow.format(TIME_FMT).toLowerCase();
            String earthText = dateStr + "   " + timeStr;
            tr.drawStringWithBackground(earthText, left, 10, titleColor, bgColor, padding);
        }

        // --- Bottom: time speed slider ---
        // We're still inside the beginFrame/endFrame block from the top of
        // renderHud(), so the orthographic projection is active. The slider
        // draws its track/handle via raw GL using the same coordinate space.
        if (physicsEngine != null) {
            renderTimeSlider(tr);
        }

        tr.endFrame();
    }

    /**
     * Map an EditField enum value to its index in the lines[] array inside
     * renderHud(). Used to look up the screen position of the field being
     * edited so we can draw the cursor at the right spot.
     */
    private int idxForField(EditField field) {
        switch (field) {
            case MASS: return 2;
            case RADIUS: return 3;
            case ECCENTRICITY: return 5;
            case SEMI_MAJOR_AXIS: return 6;
            default: return -1;
        }
    }

    /**
     * Handle a mouse click on the HUD. Returns true if the click landed on
     * an editable field (in which case editing is now active and the camera
     * should NOT start dragging).
     */
    private boolean handleHudFieldClick(double mouseX, double mouseY) {
        if (!editRectsValid) return false;
        // Convert from GLFW's top-left origin to our y-from-bottom rects.
        double myFromBottom = height - mouseY;
        for (int i = 0; i < editFieldRects.length; i++) {
            float[] r = editFieldRects[i];
            if (mouseX >= r[0] && mouseX <= r[2] && myFromBottom >= r[1] && myFromBottom <= r[3]) {
                startEditing(EditField.values()[i]);
                return true;
            }
        }
        return false;
    }

    /**
     * Begin editing the given field. The edit buffer is seeded with the
     * current value formatted as a plain string so the user can edit it.
     */
    private void startEditing(EditField field) {
        if (physicsEngine == null || selectedIndex < 0) return;
        List<CelestialBody> bodies = physicsEngine.getBodies();
        if (selectedIndex >= bodies.size()) return;
        CelestialBody body = bodies.get(selectedIndex);

        editingField = field;
        editBuffer.setLength(0);
        switch (field) {
            case MASS:
                // Seed with "1" so the user can type e.g. "2" to double the
                // current mass. The actual multiplier is computed in
                // applyEditing() against the current value.
                editBuffer.append("1");
                break;
            case RADIUS:
                editBuffer.append("1");
                break;
            case ECCENTRICITY:
                // Eccentricity is a unitless ratio, not a multiplier — keep
                // the absolute value so the user can edit it directly.
                editBuffer.append(String.format("%.4f", body.getEccentricity()));
                break;
            case SEMI_MAJOR_AXIS:
                editBuffer.append("1");
                break;
        }
        editOriginalText = editBuffer.toString();
        editCursorBlinkTimer = 0.0;
        editCursorVisible = true;
        // The buffer currently holds the seeded value; the next printable
        // character should replace it rather than append.
        editBufferDirty = false;
    }

    /** Format a float in scientific notation, e.g. 1.989e+30. */
    private String formatScientific(float value) {
        return String.format("%.3e", value);
    }

    /**
     * Handle a key event while editing. Returns true if the key was consumed
     * (so the caller should not process it as a global shortcut).
     */
    private boolean handleEditingKey(int key, int mods) {
        // Enter: commit the edit.
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            applyEditing();
            return true;
        }
        // Backspace: drop the last character.
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            if (editBuffer.length() > 0) {
                editBuffer.setLength(editBuffer.length() - 1);
            }
            editCursorBlinkTimer = 0.0;
            editCursorVisible = true;
            // Once the user starts editing (even by deleting), the buffer is
            // considered "owned" by them — subsequent typing should append.
            editBufferDirty = true;
            return true;
        }
        // Delete: clear the whole buffer.
        if (key == GLFW.GLFW_KEY_DELETE) {
            editBuffer.setLength(0);
            editCursorBlinkTimer = 0.0;
            editCursorVisible = true;
            editBufferDirty = true;
            return true;
        }
        // Printable characters: digits, '.', 'e', 'E', '+', '-'.
        char c = glfwKeyToChar(key, mods);
        if (c != 0) {
            // First printable character replaces the seeded value so the user
            // can type a fresh number (e.g. "31") instead of appending to the
            // existing scientific notation ("1.989e+30" + "31" = invalid).
            if (!editBufferDirty) {
                editBuffer.setLength(0);
                editBufferDirty = true;
            }
            editBuffer.append(c);
            editCursorBlinkTimer = 0.0;
            editCursorVisible = true;
            return true;
        }
        return false;
    }

    /** Map a GLFW key code to a printable char, or 0 if not printable. */
    private char glfwKeyToChar(int key, int mods) {
        boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        // Digits 0-9
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        }
        // Letters (used for 'e' / 'E' in scientific notation)
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char base = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(base) : base;
        }
        // Period / comma (some layouts)
        if (key == GLFW.GLFW_KEY_PERIOD) return '.';
        if (key == GLFW.GLFW_KEY_COMMA) return '.';
        // Plus / minus
        if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) return '-';
        if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) return shift ? '+' : '=';
        if (key == GLFW.GLFW_KEY_KP_ADD) return '+';
        if (key == GLFW.GLFW_KEY_SPACE) return ' ';
        return 0;
    }

    /**
     * Commit the current edit buffer to the underlying body. Invalid input
     * (non-numeric, out of range) is silently ignored and editing stays
     * active so the user can correct it.
     */
    private void applyEditing() {
        if (editingField == null || physicsEngine == null) return;
        List<CelestialBody> bodies = physicsEngine.getBodies();
        if (selectedIndex < 0 || selectedIndex >= bodies.size()) {
            cancelEditing();
            return;
        }
        CelestialBody body = bodies.get(selectedIndex);

        String text = editBuffer.toString().trim();
        if (text.isEmpty()) {
            cancelEditing();
            return;
        }

        try {
            switch (editingField) {
                case MASS: {
                    // The buffer holds a multiplier against the body's
                    // ORIGINAL reference mass (captured the first time the
                    // body was rendered). Typing "1" always restores the
                    // reference mass; typing "2" doubles it; typing "0.5"
                    // halves it — independent of the current value.
                    float multiplier = Float.parseFloat(text);
                    if (multiplier <= 0 || !Float.isFinite(multiplier)) return;
                    float refMass = referenceMass.getOrDefault(body.getName(), body.getMass());
                    body.setMass(refMass * multiplier);
                    break;
                }
                case RADIUS: {
                    float multiplier = Float.parseFloat(text);
                    if (multiplier <= 0 || !Float.isFinite(multiplier)) return;
                    float refRadius = referenceRadius.getOrDefault(body.getName(), body.getDrawRadius());
                    body.setDrawRadius(refRadius * multiplier);
                    break;
                }
                case ECCENTRICITY: {
                    // Eccentricity is a unitless ratio, not a multiplier —
                    // accept the absolute value directly.
                    double newE = Double.parseDouble(text);
                    if (newE < 0 || newE >= 1 || !Double.isFinite(newE)) return;
                    // Preserve other orbital elements; only change eccentricity.
                    body.setOrbitalParameters(
                            body.getSemiMajorAxis(),
                            newE,
                            body.getInclination(),
                            body.getAscendingNode(),
                            body.getArgOfPericenter(),
                            body.getMeanAnomaly()
                    );
                    // Recompute the orbit so the trail visualization matches.
                    CelestialBody sun = findSun(bodies);
                    if (sun != null) body.recalculateOrbit(physicsEngine, sun);
                    break;
                }
                case SEMI_MAJOR_AXIS: {
                    // Multiplier against the body's ORIGINAL reference
                    // semi-major axis (captured the first time the body was
                    // rendered). Typing "1" always restores the reference
                    // orbit; typing "2" doubles the distance from the sun.
                    double multiplier = Double.parseDouble(text);
                    if (multiplier <= 0 || !Double.isFinite(multiplier)) return;
                    double refA = referenceSemiMajorAxis.getOrDefault(body.getName(), body.getSemiMajorAxis());
                    double newA = refA * multiplier;
                    body.setOrbitalParameters(
                            newA,
                            body.getEccentricity(),
                            body.getInclination(),
                            body.getAscendingNode(),
                            body.getArgOfPericenter(),
                            body.getMeanAnomaly()
                    );
                    CelestialBody sun = findSun(bodies);
                    if (sun != null) body.recalculateOrbit(physicsEngine, sun);
                    break;
                }
            }
        } catch (NumberFormatException ex) {
            // Invalid number; keep editing so the user can fix it.
            return;
        }

        // Success: leave editing mode.
        editingField = null;
        editBuffer.setLength(0);
        editOriginalText = "";
        editBufferDirty = false;
    }

    /** Cancel the current edit and restore the original value. */
    private void cancelEditing() {
        editingField = null;
        editBuffer.setLength(0);
        editOriginalText = "";
        editBufferDirty = false;
    }

    /** Find the Sun body in the list (used as the primary for orbital recalc). */
    private CelestialBody findSun(List<CelestialBody> bodies) {
        for (CelestialBody b : bodies) {
            if ("Sun".equals(b.getName())) return b;
        }
        return null;
    }

    /**
     * Draw a yellow border around the active editing field using raw GL.
     * Coordinates are in y-from-bottom space (same as the rect cache).
     */
    /**
     * Draw a semi-transparent gray background behind the active editing field
     * to visually indicate which value is selected.
     */
    private void drawEditHighlight(float x0, float y0, float x1, float y1) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glColor4f(0.5f, 0.5f, 0.5f, 0.3f); // semi-transparent gray
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x0, y1);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private void drawEditBorder(float x0, float y0, float x1, float y1) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glLineWidth(2.0f);
        GL11.glColor4f(1.0f, 0.85f, 0.2f, 1.0f); // warm yellow
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x1, y1);
        GL11.glVertex2f(x0, y1);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * Draw a thin vertical cursor line at the end of the edit buffer.
     * Coordinates are in screen space (x from left, y from bottom).
     */
    private void drawEditCursor(int x, int y, int height) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glLineWidth(1.5f);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /**
     * Format a long value with spaces as thousand separators (Polish convention).
     * Example: 13823473323 -> "13 823 473 323"
     */
    private String formatWithSpaces(long value) {
        StringBuilder sb = new StringBuilder();
        String s = Long.toString(value);
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                sb.append(' ');
            }
            sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    // ---- Time speed slider constants ----
    private static final int SLIDER_WIDTH = 300;
    private static final int SLIDER_HEIGHT = 14;
    private static final int SLIDER_HANDLE_W = 12;
    private static final int SLIDER_MARGIN_BOTTOM = 40;

    /** Get the slider track X position (centered). */
    private int sliderTrackX() {
        return (width - SLIDER_WIDTH) / 2;
    }

    /** Get the slider track Y position (from bottom). */
    private int sliderTrackY() {
        return SLIDER_MARGIN_BOTTOM;
    }

    /** Convert a time scale value to a 0..1 position on the slider (logarithmic). */
    private double timeScaleToSliderPos(double scale) {
        if (physicsEngine == null) return 0;
        double minLog = Math.log10(physicsEngine.getMinTimeScale() + 1); // +1 to avoid log(0)
        double maxLog = Math.log10(physicsEngine.getMaxTimeScale());
        double valLog = Math.log10(Math.max(scale, 1)); // clamp to avoid log(<1)
        return (valLog - minLog) / (maxLog - minLog);
    }

    /** Convert a 0..1 slider position to a time scale value (logarithmic). */
    private double sliderPosToTimeScale(double pos) {
        if (physicsEngine == null) return 0;
        double minLog = Math.log10(physicsEngine.getMinTimeScale() + 1);
        double maxLog = Math.log10(physicsEngine.getMaxTimeScale());
        double valLog = minLog + pos * (maxLog - minLog);
        return Math.pow(10, valLog);
    }

    /** Check if a mouse click (screen coords) falls on the slider track or handle. */
    private boolean isClickOnSlider(double mouseX, double mouseY) {
        if (physicsEngine == null) return false;
        int sx = sliderTrackX();
        int sy = sliderTrackY();
        // GLFW y is from top, but our HUD uses y from bottom via ortho projection.
        // Convert: screenY (from top) → y-from-bottom = height - mouseY
        double myFromBottom = height - mouseY;
        return mouseX >= sx - SLIDER_HANDLE_W && mouseX <= sx + SLIDER_WIDTH + SLIDER_HANDLE_W
                && myFromBottom >= sy - SLIDER_HANDLE_W && myFromBottom <= sy + SLIDER_HEIGHT + SLIDER_HANDLE_W;
    }

    /** Update the time scale based on mouse X position (while dragging slider). */
    private void updateTimeScaleFromMouse(double mouseX) {
        if (physicsEngine == null) return;
        int sx = sliderTrackX();
        double pos = (mouseX - sx) / SLIDER_WIDTH;
        pos = Math.max(0, Math.min(1, pos));
        double newScale = sliderPosToTimeScale(pos);
        // Snap to 0 if very close to the left edge
        if (pos < 0.02) newScale = 0;
        physicsEngine.setTimeScale(newScale);
    }

    /** Render the time speed slider at the bottom of the screen. */
    private void renderTimeSlider(TextRenderer tr) {
        double scale = physicsEngine.getTimeScale();
        double pos = timeScaleToSliderPos(scale);

        int sx = sliderTrackX();
        int sy = sliderTrackY();
        int padding = 6;
        int lineHeight = tr.getCharHeight() + 2;

        // Format the speed label
        String speedLabel;
        if (scale == 0) {
            speedLabel = "PAUSED";
        } else if (scale < 1e3) {
            speedLabel = String.format("%.0fx", scale);
        } else if (scale < 1e6) {
            speedLabel = String.format("%.0fkx", scale / 1e3);
        } else if (scale < 1e9) {
            speedLabel = String.format("%.1fMx", scale / 1e6);
        } else {
            speedLabel = String.format("%.1fGx", scale / 1e9);
        }

        float[] labelColor = {1.0f, 1.0f, 1.0f, 1.0f};
        float[] hintColor  = {0.6f, 0.6f, 0.6f, 1.0f};
        float[] bgColor    = {0.0f, 0.0f, 0.0f, 0.6f};

        // Label above slider
        String labelLine = "Time Speed: " + speedLabel;
        tr.drawStringWithBackground(labelLine, sx, sy + SLIDER_HEIGHT + 4, labelColor, bgColor, padding);

        // Hint below slider
        tr.drawStringWithBackground("UP/DOWN: adjust | SPACE: pause | R: reset", sx, sy - lineHeight - 2, hintColor, bgColor, padding);

        // Draw slider track (background).
        // We're already inside the beginFrame/endFrame block from renderHud(),
        // so the orthographic projection is active. Draw the slider using raw
        // GL primitives directly — no nested beginFrame() call.
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(0.2f, 0.2f, 0.2f, 0.8f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(sx, sy);
        GL11.glVertex2f(sx + SLIDER_WIDTH, sy);
        GL11.glVertex2f(sx + SLIDER_WIDTH, sy + SLIDER_HEIGHT);
        GL11.glVertex2f(sx, sy + SLIDER_HEIGHT);
        GL11.glEnd();

        // Draw filled portion (from left to current position)
        float fillX = (float) (sx + pos * SLIDER_WIDTH);
        GL11.glColor4f(0.2f, 0.6f, 1.0f, 0.9f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(sx, sy);
        GL11.glVertex2f(fillX, sy);
        GL11.glVertex2f(fillX, sy + SLIDER_HEIGHT);
        GL11.glVertex2f(sx, sy + SLIDER_HEIGHT);
        GL11.glEnd();

        // Draw handle
        float handleX = fillX - SLIDER_HANDLE_W / 2f;
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(handleX, sy - 2);
        GL11.glVertex2f(handleX + SLIDER_HANDLE_W, sy - 2);
        GL11.glVertex2f(handleX + SLIDER_HANDLE_W, sy + SLIDER_HEIGHT + 2);
        GL11.glVertex2f(handleX, sy + SLIDER_HEIGHT + 2);
        GL11.glEnd();

        // Draw track border
        GL11.glColor4f(0.5f, 0.5f, 0.5f, 1.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex2f(sx, sy);
        GL11.glVertex2f(sx + SLIDER_WIDTH, sy);
        GL11.glVertex2f(sx + SLIDER_WIDTH, sy + SLIDER_HEIGHT);
        GL11.glVertex2f(sx, sy + SLIDER_HEIGHT);
        GL11.glEnd();

        // Re-enable texture for subsequent text rendering
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private float[] colorFor(String name) {
        switch (name) {
            case "Sun":     return new float[]{1.0f, 1.0f, 0.0f};     // Bright yellow
            case "Mercury": return new float[]{0.7f, 0.7f, 0.65f};    // Pale gray
            case "Venus":   return new float[]{0.95f, 0.85f, 0.55f};  // Pale yellow
            case "Earth":   return new float[]{0.15f, 0.45f, 0.85f};  // Blue
            case "Mars":    return new float[]{0.85f, 0.4f, 0.15f};   // Red-orange
            case "Jupiter": return new float[]{0.85f, 0.7f, 0.5f};    // Tan
            case "Saturn":  return new float[]{0.9f, 0.8f, 0.55f};    // Pale gold
            case "Uranus":  return new float[]{0.65f, 0.85f, 0.9f};   // Pale cyan
            case "Neptune": return new float[]{0.3f, 0.45f, 0.85f};   // Deep blue
            default:        return new float[]{0.8f, 0.8f, 0.8f};     // Gray
        }
    }

    /** Per-body specular intensity (RGB). Gas giants get less specular (more matte). */
    private float[] specularFor(String name) {
        switch (name) {
            case "Sun":     return new float[]{0.0f, 0.0f, 0.0f, 1.0f};
            case "Mercury": return new float[]{0.4f, 0.4f, 0.4f, 1.0f};
            case "Venus":   return new float[]{0.3f, 0.3f, 0.3f, 1.0f};
            case "Earth":   return new float[]{0.6f, 0.6f, 0.6f, 1.0f};
            case "Mars":    return new float[]{0.2f, 0.2f, 0.2f, 1.0f};
            case "Jupiter": return new float[]{0.1f, 0.1f, 0.1f, 1.0f};
            case "Saturn":  return new float[]{0.15f, 0.15f, 0.15f, 1.0f};
            case "Uranus":  return new float[]{0.4f, 0.4f, 0.4f, 1.0f};
            case "Neptune": return new float[]{0.5f, 0.5f, 0.5f, 1.0f};
            default:        return new float[]{0.3f, 0.3f, 0.3f, 1.0f};
        }
    }

    /** Per-body shininess exponent (higher = smaller, sharper highlight). */
    private float shininessFor(String name) {
        switch (name) {
            case "Earth":   return 64.0f;
            case "Mercury": return 48.0f;
            case "Venus":   return 24.0f;
            case "Mars":    return 16.0f;
            case "Jupiter": return 8.0f;
            case "Saturn":  return 8.0f;
            case "Uranus":  return 32.0f;
            case "Neptune": return 48.0f;
            default:        return SHININESS;
        }
    }

    /**
     * Apply the body's axial rotation to the current OpenGL model-view matrix.
     * Must be called AFTER {@code glTranslatef(x,y,z)} (so the rotation pivots
     * around the body's centre) and BEFORE the sphere draw call.
     *
     * <p>The rotation is composed of two parts:
     * <ol>
     *   <li><b>Tilt</b> — rotate the spin axis away from the orbital-plane
     *       normal (Y) by {@code axialTilt} radians around the X axis. This
     *       gives the body its characteristic obliquity (Earth's 23.4°,
     *       Uranus' 97.8°, etc.).</li>
     *   <li><b>Spin</b> — rotate around the (now tilted) spin axis by the
     *       accumulated {@code rotationAngle}. A negative rotation period
     *       naturally produces retrograde spin because {@code rotationAngle}
     *       decreases over time.</li>
     * </ol>
     *
     * <p>Bodies with a rotation period of zero (or no rotation data) are
     * left untouched, so static bodies still render correctly.
     */
    private void applyAxialRotation(CelestialBody body) {
        double tilt = body.getAxialTilt();
        double angle = body.getRotationAngle();
        if (tilt == 0.0 && angle == 0.0) {
            return;
        }
        // 1. Tilt the spin axis away from +Y. We rotate around the X axis so
        //    that the north pole leans toward +Z (the conventional direction
        //    for prograde bodies in this simulator's XZ-orbital-plane frame).
        if (tilt != 0.0) {
            GL11.glRotated(Math.toDegrees(tilt), 1.0, 0.0, 0.0);
        }
        // 2. Spin around the (now tilted) local Y axis.
        if (angle != 0.0) {
            GL11.glRotated(Math.toDegrees(angle), 0.0, 1.0, 0.0);
        }
    }

    /**
     * Apply ONLY the axial tilt (no spin) to the current OpenGL model-view
     * matrix. Used for drawing Saturn's rings, which must stay aligned with
     * the planet's equatorial plane but should not rotate with the body.
     */
    private void applyRingTilt(CelestialBody body) {
        double tilt = body.getAxialTilt();
        if (tilt != 0.0) {
            GL11.glRotated(Math.toDegrees(tilt), 1.0, 0.0, 0.0);
        }
    }

    /**
     * Draw a flat ring (annulus) in the local XZ plane, centered at the
     * current origin. The ring is built as a triangle strip with the given
     * number of segments. UVs run from 0 at the inner edge to 1 at the outer
     * edge so the alpha texture can fade the ring naturally.
     */
    private void drawRing(float innerRadius, float outerRadius, int segments) {
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float theta = (float) (2.0 * Math.PI * i / segments);
            float cosT = (float) Math.cos(theta);
            float sinT = (float) Math.sin(theta);
            float u = (float) i / segments;

            // Outer vertex
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glNormal3f(0, 1, 0);
            GL11.glVertex3f(cosT * outerRadius, 0, sinT * outerRadius);

            // Inner vertex
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glNormal3f(0, 1, 0);
            GL11.glVertex3f(cosT * innerRadius, 0, sinT * innerRadius);
        }
        GL11.glEnd();
    }

    /** Initialize the starfield: random points on a large sphere with varied colors. */
    private void initStarfield() {
        if (starsInitialized) return;
        java.util.Random rng = new java.util.Random(42L); // deterministic seed
        float radius = 5000.0f; // far away in scene units
        for (int i = 0; i < STAR_COUNT; i++) {
            // Uniform random direction on a sphere
            double u = rng.nextDouble() * 2.0 - 1.0;
            double theta = rng.nextDouble() * 2.0 * Math.PI;
            double sq = Math.sqrt(1.0 - u * u);
            float sx = (float) (sq * Math.cos(theta)) * radius;
            float sy = (float) u * radius;
            float sz = (float) (sq * Math.sin(theta)) * radius;
            starPositions[i * 3]     = sx;
            starPositions[i * 3 + 1] = sy;
            starPositions[i * 3 + 2] = sz;

            // Slight color variation: white-ish with subtle tint
            float tint = (float) rng.nextDouble();
            if (tint < 0.6f) {
                // White
                starColors[i * 3]     = 1.0f;
                starColors[i * 3 + 1] = 1.0f;
                starColors[i * 3 + 2] = 1.0f;
            } else if (tint < 0.8f) {
                // Bluish
                starColors[i * 3]     = 0.7f;
                starColors[i * 3 + 1] = 0.85f;
                starColors[i * 3 + 2] = 1.0f;
            } else if (tint < 0.95f) {
                // Yellowish
                starColors[i * 3]     = 1.0f;
                starColors[i * 3 + 1] = 0.95f;
                starColors[i * 3 + 2] = 0.7f;
            } else {
                // Reddish
                starColors[i * 3]     = 1.0f;
                starColors[i * 3 + 1] = 0.7f;
                starColors[i * 3 + 2] = 0.6f;
            }
        }
        starsInitialized = true;
    }

    /** Draw a unit-based sphere of the given radius using a lat/long mesh of quads. */
    private void drawSphere(float radius, int stacks, int slices) {
        for (int i = 0; i < stacks; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / stacks);
            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / stacks);
            float y0 = (float) Math.sin(lat0);
            float y1 = (float) Math.sin(lat1);
            float r0 = (float) Math.cos(lat0);
            float r1 = (float) Math.cos(lat1);

            // Equirectangular UV: v = 0 at south pole, v = 1 at north pole.
            float v0 = (float) i / stacks;
            float v1 = (float) (i + 1) / stacks;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                double lng = 2.0 * Math.PI * (double) j / slices;
                float xCos = (float) Math.cos(lng);
                float zSin = (float) Math.sin(lng);

                // u wraps around the sphere; clamp to [0, 1) to avoid sampling the seam twice.
                float u = (float) j / slices;

                // Vertex on the lower stack edge
                GL11.glNormal3f(xCos * r0, y0, zSin * r0);
                GL11.glTexCoord2f(u, v0);
                GL11.glVertex3f(radius * xCos * r0, radius * y0, radius * zSin * r0);

                // Vertex on the upper stack edge
                GL11.glNormal3f(xCos * r1, y1, zSin * r1);
                GL11.glTexCoord2f(u, v1);
                GL11.glVertex3f(radius * xCos * r1, radius * y1, radius * zSin * r1);
            }
            GL11.glEnd();
        }


    }

    /**
     * Draw a sphere with night-side illumination: vertices facing away from the
     * sun are bright (city lights), vertices facing the sun are dark.
     * Uses additive blending so the result composites over the day-lit sphere.
     */
    private void drawSphereNight(float radius, int stacks, int slices, Vector3f sunDir) {
        for (int i = 0; i < stacks; i++) {
            double lat0 = Math.PI * (-0.5 + (double) i / stacks);
            double lat1 = Math.PI * (-0.5 + (double) (i + 1) / stacks);
            float y0 = (float) Math.sin(lat0);
            float y1 = (float) Math.sin(lat1);
            float r0 = (float) Math.cos(lat0);
            float r1 = (float) Math.cos(lat1);

            float v0 = (float) i / stacks;
            float v1 = (float) (i + 1) / stacks;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                double lng = 2.0 * Math.PI * (double) j / slices;
                float xCos = (float) Math.cos(lng);
                float zSin = (float) Math.sin(lng);

                float u = (float) j / slices;

                // Lower vertex
                float nx0 = xCos * r0, ny0 = y0, nz0 = zSin * r0;
                float dot0 = nx0 * sunDir.x + ny0 * sunDir.y + nz0 * sunDir.z;
                float night0 = Math.max(0.0f, -dot0);   // bright on dark side
                night0 *= night0;                          // sharpen terminator
                GL11.glNormal3f(nx0, ny0, nz0);
                GL11.glTexCoord2f(u, v0);
                GL11.glColor3f(night0, night0, night0);
                GL11.glVertex3f(radius * nx0, radius * ny0, radius * nz0);

                // Upper vertex
                float nx1 = xCos * r1, ny1 = y1, nz1 = zSin * r1;
                float dot1 = nx1 * sunDir.x + ny1 * sunDir.y + nz1 * sunDir.z;
                float night1 = Math.max(0.0f, -dot1);
                night1 *= night1;
                GL11.glNormal3f(nx1, ny1, nz1);
                GL11.glTexCoord2f(u, v1);
                GL11.glColor3f(night1, night1, night1);
                GL11.glVertex3f(radius * nx1, radius * ny1, radius * nz1);
            }
            GL11.glEnd();
        }
    }

    public boolean shouldClose() {
        return shouldClose;
    }

    public void cleanup() {
        if (textRenderer != null) {
            textRenderer.dispose();
            textRenderer = null;
        }
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }


}
