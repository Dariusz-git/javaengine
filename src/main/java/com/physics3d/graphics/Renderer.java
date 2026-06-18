package com.physics3d.graphics;

import com.physics3d.engine.PhysicsEngine;
import com.physics3d.model.CelestialBody;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.awt.Font;
import java.nio.FloatBuffer;
import java.util.List;
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
    // Minimum drawn radius in scene units so bodies stay visible at system scale
    private static final float MIN_DRAW_RADIUS = 5e9f * WORLD_SCALE;
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

    // ---- HUD / planet selection ----
    private TextRenderer textRenderer;
    private int selectedIndex = -1; // -1 = no selection; otherwise index into the body list
    private boolean tabPressed = false; // edge-detect for Tab key
    private boolean tabShift = false;   // true when Tab is pressed together with Shift
    private boolean trackingEnabled = false; // true when camera follows the selected body

    // ---- Time speed control ----
    private PhysicsEngine physicsEngine;
    private boolean sliderDragging = false; // true when user is dragging the slider handle

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
                shouldClose = true;
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

        // Mouse button: start/stop dragging to rotate the camera, or slider
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
            if (camDistance < 10.0f) camDistance = 10.0f;
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

        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        setupProjection();

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

        // ---- Draw actual trajectory (from simulation) ----
        GL11.glColor3f(0.8f, 0.8f, 0.2f);
        for (CelestialBody body : bodies) {
            Queue<Vector3f> actual = body.getTrail().getPositions();
            if (actual.size() > 1) {
                GL11.glBegin(GL11.GL_LINE_STRIP);
                for (Vector3f pos : actual) {
                    GL11.glVertex3f(pos.x * WORLD_SCALE, pos.y * WORLD_SCALE, pos.z * WORLD_SCALE);
                }
                GL11.glEnd();
            }
        }

        // ---- Draw orbital trails ----
        if (showTrails) {
            GL11.glLineWidth(1.0f);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            for (CelestialBody body : bodies) {
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

                Queue<Vector3f> positions = body.getTrail().getPositions();
                if (positions.size() > 1) {
                    GL11.glColor3f(0.8f, 0.8f, 0.2f);
                    GL11.glBegin(GL11.GL_LINE_STRIP);
                    Vector3f[] posArray = positions.toArray(new Vector3f[0]);
                    for (Vector3f pos : posArray) {
                        GL11.glVertex3f(pos.x * WORLD_SCALE, pos.y * WORLD_SCALE, pos.z * WORLD_SCALE);
                    }
                    GL11.glEnd();
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
            float r = Math.max(body.getRadius() * WORLD_SCALE, MIN_DRAW_RADIUS);

            float[] baseColor = colorFor(body.getName());

            if ("Sun".equals(body.getName())) {
                // Sun: emit light, no shading. Disable lighting for the Sun itself.
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glColor3f(baseColor[0], baseColor[1], baseColor[2]);
                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, z);
                drawSphere(r, SPHERE_STACKS, SPHERE_SLICES);
                GL11.glPopMatrix();
                // Draw atmospheric glow around the Sun
                drawSunGlow(x, y, z, r);
                GL11.glEnable(GL11.GL_LIGHTING);
            } else {
                // Planets: use OpenGL lighting with per-vertex normals
                // Set the base color (diffuse + ambient via COLOR_MATERIAL)
                GL11.glColor3f(baseColor[0], baseColor[1], baseColor[2]);
                // Set specular intensity per body (gas giants get less specular)
                float[] spec = specularFor(body.getName());
                GL11.glMaterialfv(GL11.GL_FRONT_AND_BACK, GL11.GL_SPECULAR, spec);
                GL11.glMaterialf(GL11.GL_FRONT_AND_BACK, GL11.GL_SHININESS, shininessFor(body.getName()));

                GL11.glPushMatrix();
                GL11.glTranslatef(x, y, z);
                drawSphere(r, SPHERE_STACKS, SPHERE_SLICES);
                GL11.glPopMatrix();
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

    /** Draw a soft glow around the Sun using additive blending. */
    private void drawSunGlow(float x, float y, float z, float r) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // additive
        GL11.glDepthMask(false);

        // Draw several concentric transparent shells for a corona effect
        int layers = 6;
        for (int layer = 1; layer <= layers; layer++) {
            float shellRadius = r * (1.0f + layer * 0.6f);
            float alpha = 0.18f / layer;
            // Warm corona color
            GL11.glColor4f(1.0f, 0.85f, 0.4f, alpha);
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, z);
            drawSphere(shellRadius, 12, 16);
            GL11.glPopMatrix();
        }

        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_BLEND);
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

            String[] lines = {
                    "Selected: " + body.getName(),
                    String.format("Velocity: %.3e m/s", speed),
                    String.format("Mass:     %.3e kg", mass),
                    String.format("Radius:   %.3e m", radius),
                    String.format("Dist Sun: %.3e m", distanceFromSun),
                    String.format("Eccentr.: %.4f", body.getEccentricity()),
                    String.format("SemiMaj.: %.3e m", body.getSemiMajorAxis())
            };

            int panelWidth = 320;
            int right = width - panelWidth - 10;
            int panelTop = top;
            for (int i = 0; i < lines.length; i++) {
                float[] color = (i == 0) ? titleColor : new float[]{0.95f, 0.95f, 0.95f, 1.0f};
                tr.drawStringWithBackground(lines[i], right, panelTop - (i + 1) * lineHeight, color, bgColor, padding);
            }
        }

        // --- Bottom-left: universe age ---
        if (physicsEngine != null) {
            double ageYears = physicsEngine.getUniverseAgeYears();
            String ageText = "Wiek wszechświata: " + formatWithSpaces((long) ageYears) + " lat";
            tr.drawStringWithBackground(ageText, left, 10, titleColor, bgColor, padding);
        }

        // --- Bottom: time speed slider ---
        if (physicsEngine != null) {
            renderTimeSlider(tr);
        }

        tr.endFrame();
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

        // Draw slider track (background)
        tr.beginFrame(width, height); // already in a frame, but we draw with GL directly
        // We need to draw the slider using raw GL since TextRenderer only does text.
        // Switch to 2D ortho for slider drawing (already set up by beginFrame).
        // Actually, we're already in the beginFrame/endFrame block, so ortho is set.
        // We need to draw GL primitives here. Let's draw them.

        // Draw track background
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

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (int j = 0; j <= slices; j++) {
                double lng = 2.0 * Math.PI * (double) j / slices;
                float xCos = (float) Math.cos(lng);
                float zSin = (float) Math.sin(lng);

                // Vertex on the lower stack edge
                GL11.glNormal3f(xCos * r0, y0, zSin * r0);
                GL11.glVertex3f(radius * xCos * r0, radius * y0, radius * zSin * r0);

                // Vertex on the upper stack edge
                GL11.glNormal3f(xCos * r1, y1, zSin * r1);
                GL11.glVertex3f(radius * xCos * r1, radius * y1, radius * zSin * r1);
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
