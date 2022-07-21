package com.kienht.bubblepicker.physics

import org.jbox2d.collision.shapes.CircleShape
import org.jbox2d.collision.shapes.Shape
import org.jbox2d.common.Vec2
import org.jbox2d.dynamics.*
import kotlin.jvm.internal.Intrinsics
import kotlin.math.abs

/**
 * Created by irinagalata on 1/26/17.
 */
class CircleBody(val world: World, var position: Vec2, var radius: Float, var increasedRadius: Float, var density: Float,
                 val isAlwaysSelected: Boolean) {

    val decreasedRadius: Float = radius

    var isIncreasing = false

    var isDecreasing = false

    var toBeIncreased = false

    var toBeDecreased = false

    val finished: Boolean
        get() = !toBeIncreased && !toBeDecreased && !isIncreasing && !isDecreasing

    val isBusy: Boolean
        get() = isIncreasing || isDecreasing

    lateinit var physicalBody: Body

    var increased = false

    var isVisible = true

    private val margin = 0.01f
    private val damping = 25f
    private val shape: CircleShape
        get() = CircleShape().apply {
            m_radius = radius + margin
            m_p.setZero()
        }

    private val fixture: FixtureDef
        get() = FixtureDef().apply {
            this.shape = this@CircleBody.shape
            this.density = this@CircleBody.density
        }

    private val bodyDef: BodyDef
        get() = BodyDef().apply {
            type = BodyType.DYNAMIC
            this.position = this@CircleBody.position
        }

    init {
        while (true) {
            if (world.isLocked.not()) {
                initializeBody()
                break
            }
        }
    }

    private fun initializeBody() {
        physicalBody = world.createBody(bodyDef).apply {
            createFixture(fixture)
            linearDamping = damping
        }
    }

    fun resize(step: Float) {
        if (this.isAlwaysSelected) {
            if (!this.increased) {
                increase(step);
            }
        } else if (this.increased) {
            decrease(step);
        } else {
            increase(step);
        }
    }

    fun decrease(step: Float) {
        this.isDecreasing = true;
        this.radius -= step;
        reset();
        if (abs(this.radius - this.decreasedRadius) < step) {
            this.increased = false;
            clear();
        }
    }

    fun increase(step: Float) {
        this.isIncreasing = true;
        this.radius += step;
        reset();
        if (Math.abs(this.radius - this.increasedRadius) < step) {
            this.increased = true;
            clear();
        }
    }

    private fun reset() {
        var mShape = shape
        val body = physicalBody
        val fixtureList = body.fixtureList
        if (fixtureList != null && fixtureList.shape.also { mShape = it as CircleShape } != null) {
            mShape.m_radius = radius + margin
        }
    }

    fun defineState() {
        toBeIncreased = !increased
        toBeDecreased = increased
    }

    private fun clear() {
        toBeIncreased = false
        toBeDecreased = false
        isIncreasing = false
        isDecreasing = false
    }

}