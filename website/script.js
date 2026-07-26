/* ==========================================================================
   WALVO MUSIC - INTERACTIVE 3D & AUDIO VISUALIZER ENGINE
   ========================================================================== */

document.addEventListener("DOMContentLoaded", () => {
    // --------------------------------------------------------------------------
    // 1. Mobile Menu Toggle
    // --------------------------------------------------------------------------
    const mobileToggle = document.getElementById("mobile-toggle");
    const mobileMenu = document.getElementById("mobile-menu");

    if (mobileToggle && mobileMenu) {
        mobileToggle.addEventListener("click", () => {
            mobileMenu.classList.toggle("active");
            const icon = mobileToggle.querySelector("i");
            if (mobileMenu.classList.contains("active")) {
                icon.classList.replace("fa-bars", "fa-xmark");
            } else {
                icon.classList.replace("fa-xmark", "fa-bars");
            }
        });

        // Close menu on link click
        document.querySelectorAll(".mobile-link").forEach(link => {
            link.addEventListener("click", () => {
                mobileMenu.classList.remove("active");
                mobileToggle.querySelector("i").classList.replace("fa-xmark", "fa-bars");
            });
        });
    }

    // --------------------------------------------------------------------------
    // 1b. Smooth Scroll without modifying URL hash
    // --------------------------------------------------------------------------
    document.querySelectorAll("[data-scroll]").forEach(link => {
        link.addEventListener("click", (e) => {
            e.preventDefault();
            const targetId = link.getAttribute("data-scroll");
            const targetEl = document.getElementById(targetId);
            if (targetEl) {
                targetEl.scrollIntoView({ behavior: "smooth" });
            }
        });
    });

    // --------------------------------------------------------------------------
    // 2. 3D Card Tilt Mouse Effect (Hero Card)
    // --------------------------------------------------------------------------
    const tiltCard = document.getElementById("tilt-card");

    if (tiltCard) {
        document.addEventListener("mousemove", (e) => {
            const { innerWidth, innerHeight } = window;
            const mouseX = (e.clientX - innerWidth / 2) / (innerWidth / 2);
            const mouseY = (e.clientY - innerHeight / 2) / (innerHeight / 2);

            const rotateX = -mouseY * 15; // Limit rotation angle
            const rotateY = mouseX * 15;

            tiltCard.style.transform = `rotateX(${rotateX}deg) rotateY(${rotateY}deg)`;
        });

        // Reset tilt on mouse leave
        document.addEventListener("mouseleave", () => {
            tiltCard.style.transform = `rotateX(0deg) rotateY(0deg)`;
        });
    }

    // --------------------------------------------------------------------------
    // 3. Ambient Starfield Particle Canvas
    // --------------------------------------------------------------------------
    const canvas = document.getElementById("ambient-canvas");
    if (canvas) {
        const ctx = canvas.getContext("2d");
        let particles = [];

        function resizeCanvas() {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }

        window.addEventListener("resize", resizeCanvas);
        resizeCanvas();

        class Particle {
            constructor() {
                this.reset();
            }

            reset() {
                this.x = Math.random() * canvas.width;
                this.y = Math.random() * canvas.height;
                this.size = Math.random() * 2 + 0.5;
                this.speedX = (Math.random() - 0.5) * 0.4;
                this.speedY = (Math.random() - 0.5) * 0.4;
                this.alpha = Math.random() * 0.5 + 0.2;
            }

            update() {
                this.x += this.speedX;
                this.y += this.speedY;

                if (this.x < 0 || this.x > canvas.width || this.y < 0 || this.y > canvas.height) {
                    this.reset();
                }
            }

            draw() {
                ctx.fillStyle = `rgba(0, 242, 254, ${this.alpha})`;
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
                ctx.fill();
            }
        }

        for (let i = 0; i < 70; i++) {
            particles.push(new Particle());
        }

        function animateParticles() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            particles.forEach((p) => {
                p.update();
                p.draw();
            });
            requestAnimationFrame(animateParticles);
        }

        animateParticles();
    }

    // --------------------------------------------------------------------------
    // 4. Hero Visualizer Waveform Canvas Animation
    // --------------------------------------------------------------------------
    const heroCanvas = document.getElementById("hero-visualizer-canvas");
    if (heroCanvas) {
        const hCtx = heroCanvas.getContext("2d");
        let phase = 0;

        function drawHeroWave() {
            hCtx.clearRect(0, 0, heroCanvas.width, heroCanvas.height);
            hCtx.beginPath();
            hCtx.lineWidth = 3;

            const gradient = hCtx.createLinearGradient(0, 0, heroCanvas.width, 0);
            gradient.addColorStop(0, "#00F2FE");
            gradient.addColorStop(0.5, "#4FACFE");
            gradient.addColorStop(1, "#FF0844");
            hCtx.strokeStyle = gradient;

            const midY = heroCanvas.height / 2;
            hCtx.moveTo(0, midY);

            for (let x = 0; x < heroCanvas.width; x++) {
                const angle = (x / heroCanvas.width) * Math.PI * 4 + phase;
                const y = midY + Math.sin(angle) * 12 * Math.cos(phase * 0.5);
                hCtx.lineTo(x, y);
            }

            hCtx.stroke();
            phase += 0.05;
            requestAnimationFrame(drawHeroWave);
        }

        drawHeroWave();
    }

    // --------------------------------------------------------------------------
    // 5. Interactive Audio Synth & Waveform Visualizer (Demo Section)
    // --------------------------------------------------------------------------
    const demoPlayBtn = document.getElementById("demo-play-btn");
    const demoPlayIcon = document.getElementById("demo-play-icon");
    const demoPlayText = document.getElementById("demo-play-text");
    const demoProgressFill = document.getElementById("demo-progress-fill");
    const currTime = document.getElementById("curr-time");
    const waveCanvas = document.getElementById("interactive-waveform-canvas");

    let isPlaying = false;
    let audioCtx = null;
    let oscillator = null;
    let gainNode = null;
    let animId = null;
    let progressSeconds = 0;
    let progressTimer = null;

    if (waveCanvas) {
        const wCtx = waveCanvas.getContext("2d");

        function drawWaveformBars() {
            wCtx.clearRect(0, 0, waveCanvas.width, waveCanvas.height);
            const barWidth = 6;
            const gap = 4;
            const totalBars = Math.floor(waveCanvas.width / (barWidth + gap));

            for (let i = 0; i < totalBars; i++) {
                let barHeight;
                if (isPlaying) {
                    barHeight = Math.random() * (waveCanvas.height * 0.7) + 10;
                } else {
                    barHeight = Math.sin(i * 0.2) * 20 + 25;
                }

                const x = i * (barWidth + gap);
                const y = (waveCanvas.height - barHeight) / 2;

                const grad = wCtx.createLinearGradient(0, y, 0, y + barHeight);
                if (isPlaying) {
                    grad.addColorStop(0, "#00F2FE");
                    grad.addColorStop(1, "#FF0844");
                } else {
                    grad.addColorStop(0, "#365194");
                    grad.addColorStop(1, "#11131E");
                }

                wCtx.fillStyle = grad;
                wCtx.beginPath();
                wCtx.roundRect(x, y, barWidth, barHeight, 3);
                wCtx.fill();
            }

            animId = requestAnimationFrame(drawWaveformBars);
        }

        drawWaveformBars();
    }

    if (demoPlayBtn) {
        demoPlayBtn.addEventListener("click", () => {
            isPlaying = !isPlaying;

            if (isPlaying) {
                demoPlayIcon.classList.replace("fa-play", "fa-pause");
                demoPlayText.textContent = "Pause Audio Demo";

                // Start Synthetic Arpeggiator Melody
                startAudioSynth();

                // Progress bar timer
                progressTimer = setInterval(() => {
                    progressSeconds = (progressSeconds + 0.1) % 30;
                    const percent = (progressSeconds / 30) * 100;
                    demoProgressFill.style.width = `${percent}%`;

                    const mins = Math.floor(progressSeconds / 60);
                    const secs = Math.floor(progressSeconds % 60).toString().padStart(2, '0');
                    currTime.textContent = `${mins}:${secs}`;
                }, 100);
            } else {
                stopAudioSynth();
                demoPlayIcon.classList.replace("fa-pause", "fa-play");
                demoPlayText.textContent = "Play Audio Demo";

                clearInterval(progressTimer);
            }
        });
    }

    function startAudioSynth() {
        if (!audioCtx) {
            audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        }
        
        if (audioCtx.state === 'suspended') {
            audioCtx.resume();
        }

        // Generate ambient melody notes (C Minor Pentatonic)
        const notes = [261.63, 311.13, 349.23, 392.00, 466.16, 523.25];
        let noteIndex = 0;

        oscillator = audioCtx.createOscillator();
        gainNode = audioCtx.createGain();

        oscillator.type = 'triangle';
        gainNode.gain.setValueAtTime(0.08, audioCtx.currentTime);

        oscillator.connect(gainNode);
        gainNode.connect(audioCtx.destination);

        oscillator.start();

        const synthInterval = setInterval(() => {
            if (!isPlaying) {
                clearInterval(synthInterval);
                return;
            }
            const freq = notes[noteIndex % notes.length];
            oscillator.frequency.setValueAtTime(freq, audioCtx.currentTime);
            noteIndex++;
        }, 200);
    }

    function stopAudioSynth() {
        if (oscillator) {
            try {
                oscillator.stop();
                oscillator.disconnect();
            } catch (e) {}
        }
    }

    // --------------------------------------------------------------------------
    // 6. FAQ Accordion Interaction
    // --------------------------------------------------------------------------
    const faqItems = document.querySelectorAll(".faq-item");

    faqItems.forEach((item) => {
        item.addEventListener("click", () => {
            const isActive = item.classList.contains("active");

            // Close all items
            faqItems.forEach((el) => el.classList.remove("active"));

            // Toggle clicked item
            if (!isActive) {
                item.classList.add("active");
            }
        });
    });
});
