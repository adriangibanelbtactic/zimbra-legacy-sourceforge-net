/*
 * Copyright (C) 2006, The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 *
 * An object useful for implementing accelerated / decelerated animation.
 *
 * @author Mihai Bazon <mihai@zimbra.com>
 *
 * Arguments:
 *
 *    length   -- the number of frames
 *    speed    -- speed of the timer (i.e. argument to setInterval)
 *    f        -- easing function
 *    onUpdate -- AjxCallback that will be called when the timer updates
 *    onStop   -- AjxCallback -- called when the animation is finished
 *
 * This object creates a timer (setInterval) and calls the function
 * (AjxCallback) that you supply at onUpdate every "speed" milliseconds,
 * passing to it two arguments:
 *
 * - pos -- a float number between 0 and 1
 * - anim -- the animation object
 *
 * "pos" is computed by an easing function; it is 0 when the animation starts
 * and it approaches 1 as your animation continues; For example, depending on
 * the easing function, it can increase faster initially and slower as it
 * reaches 1 (simulates acceleration).
 *
 * AjxAnimation supplies 2 very basic easing functions (f_accelerate and
 * f_decelerate).  More complex functions can be easily written, to simulate
 * i.e. bouncing -- you just have to find the mathematical representation.
 *
 * An easing function receives 2 arguments:
 *
 * - i -- the current frame
 * - length -- the total number of frames
 *
 * It should return 0 when i == 0, 1 when i == length, and a number between 0
 * and 1 otherwise.  You can get a constant speed animation with this
 * uninteresting easing function:
 *
 *    function(i, length) { return i / length; }
 *
 */

AjxAnimation = function(args) {
	this.length = args.length || 15;
	this.speed = args.speed || 50;
	this.f = args.f || AjxAnimation.f_decelerate;
	this.onUpdate = args.onUpdate;
	this.onStop = args.onStop;

	this.__work = AjxCallback.simpleClosure(this.__work, this);
};

AjxAnimation.prototype.start = function() {
	this.i = 0;
	this.stop();
	this.timer = setInterval(this.__work, this.speed);
};

AjxAnimation.prototype.stop = function() {
	if (this.timer) {
		clearInterval(this.timer);
		this.timer = null;
		if (this.onStop)
			this.onStop.run(this);
	}
};

AjxAnimation.prototype.__work = function() {
	this.onUpdate.run(this.f(this.i++, this.length),
			  this);
	if (this.i > this.length)
		this.stop();
};

// if pos is in [0, 1], this function maps it to the interval [a, b]
AjxAnimation.prototype.map = function(pos, a, b) {
	return a + (b - a) * pos;
};

// simple easing functions for acceleration / deceleration

AjxAnimation.f_decelerate = function(i, l) {
	var x = 1 - i/l;
	x = x * x;
	return 1 - x * x;
};

AjxAnimation.f_accelerate = function(i, l) {
	var x = i/l;
	x = x * x;
	return x * x;
};

AjxAnimation.f_plain = function(i, l) {
	return i / l;
};
