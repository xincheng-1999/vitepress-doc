<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { withBase } from 'vitepress'

// ── Typewriter role cycling ──────────────────────────────────────────────────
const roles = ['前端工程师', '全栈探索者', 'AI 技术爱好者', 'Flutter 开发者']
const roleIdx = ref(0)
const roleVisible = ref(true)
let roleTimer = null

// ── Canvas particle network ──────────────────────────────────────────────────
let cvs = null, c2d = null, raf = null
const pts = []

function mkPt(w, h) {
  return {
    x: Math.random() * w,
    y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.55,
    vy: (Math.random() - 0.5) * 0.55,
    r: Math.random() * 1.5 + 0.5,
    a: Math.random() * 0.45 + 0.2,
  }
}

function resizeCanvas() {
  if (!cvs) return
  cvs.width = cvs.offsetWidth
  cvs.height = cvs.offsetHeight
}

async function initCanvas() {
  await nextTick()
  cvs = document.getElementById('hp-canvas')
  if (!cvs) return
  c2d = cvs.getContext('2d')
  resizeCanvas()
  for (let i = 0; i < 80; i++) pts.push(mkPt(cvs.width, cvs.height))
  window.addEventListener('resize', resizeCanvas)
  drawFrame()
}

function drawFrame() {
  if (!c2d || !cvs) return
  const W = cvs.width, H = cvs.height
  c2d.clearRect(0, 0, W, H)
  for (let i = 0; i < pts.length; i++) {
    const a = pts[i]
    for (let j = i + 1; j < pts.length; j++) {
      const b = pts[j]
      const d = Math.hypot(a.x - b.x, a.y - b.y)
      if (d < 120) {
        c2d.beginPath()
        c2d.strokeStyle = `rgba(255,255,255,${(1 - d / 120) * 0.1})`
        c2d.moveTo(a.x, a.y)
        c2d.lineTo(b.x, b.y)
        c2d.stroke()
      }
    }
    c2d.beginPath()
    c2d.arc(a.x, a.y, a.r, 0, 6.28)
    c2d.fillStyle = `rgba(255,255,255,${a.a})`
    c2d.fill()
    a.x += a.vx
    a.y += a.vy
    if (a.x < 0 || a.x > W) a.vx *= -1
    if (a.y < 0 || a.y > H) a.vy *= -1
  }
  raf = requestAnimationFrame(drawFrame)
}

// ── Navigation cards ─────────────────────────────────────────────────────────
const navCards = [
  {
    icon: '⚡', title: '前端核心',
    desc: 'HTML · CSS · JavaScript 基础，Vue 3、React 深度实践，面试手写题，Node.js 工程化',
    link: '/front-end/the-basics/js-basics/event-loop', color: '#f06292', badge: '50+',
  },
  {
    icon: '☕', title: '后端 & 数据库',
    desc: 'Java · Spring Boot CRUD，MyBatis、JPA ORM，MySQL、Redis、MongoDB 调优',
    link: '/back-end/', color: '#ff8a65', badge: '30+',
  },
  {
    icon: '🤖', title: 'AI & 大模型',
    desc: 'LLM 原理、Transformer、Prompt 工程，RAG 最简实现，Ollama 本地部署',
    link: '/llm-study/', color: '#ce93d8', badge: '10+',
  },
  {
    icon: '📱', title: 'Flutter 跨端',
    desc: 'Dart 语法过渡，Widget 布局核心，Riverpod 状态管理，Dio 网络请求',
    link: '/flutter/fe-to-flutter/', color: '#42a5f5', badge: '13+',
  },
  {
    icon: '🐍', title: 'Python 自动化',
    desc: 'pyenv 环境，文件处理，HTTP 请求，CLI 工具，Ollama Python，RAG 极简实现',
    link: '/python-study/', color: '#66bb6a', badge: '15+',
  },
  {
    icon: '🛠️', title: '工具 & 效率',
    desc: 'Git 技巧、adb 调试、内网穿透、代理配置，Windows & Mac 效率工具合集',
    link: '/IT-technology/git', color: '#ffa726', badge: '10+',
  },
]

// ── Tech tags marquee ────────────────────────────────────────────────────────
const techTags = [
  'JavaScript', 'TypeScript', 'Vue 3', 'React', 'Node.js',
  'Vite', 'VitePress', 'Pinia', 'Java', 'Spring Boot',
  'MyBatis', 'MySQL', 'Redis', 'MongoDB', 'Python',
  'Flutter', 'Dart', 'LLM', 'RAG', 'Transformer',
  'Ollama', 'Docker', 'Git', 'Axios', 'Riverpod',
]

// ── Scroll-based nav mode ────────────────────────────────────────────────────
function updateNavMode() {
  const hero = document.querySelector('.hp-hero')
  const cta = document.querySelector('.hp-cta')
  const navH = parseInt(getComputedStyle(document.documentElement).getPropertyValue('--vp-nav-height')) || 64
  const html = document.documentElement
  let inDark = false
  // Check if nav overlaps the hero
  if (hero) {
    const r = hero.getBoundingClientRect()
    if (r.top < navH && r.bottom > navH) inDark = true
  }
  // Check if nav overlaps the CTA
  if (!inDark && cta) {
    const r = cta.getBoundingClientRect()
    if (r.top < navH && r.bottom > navH) inDark = true
  }
  if (inDark) {
    html.setAttribute('data-hero', '')
  } else {
    html.removeAttribute('data-hero')
  }
}

// ── Lifecycle ────────────────────────────────────────────────────────────────
onMounted(() => {
  if (import.meta.env.SSR) return
  document.documentElement.setAttribute('data-layout', 'home')
  initCanvas()
  updateNavMode()
  window.addEventListener('scroll', updateNavMode, { passive: true })
  roleTimer = setInterval(() => {
    roleVisible.value = false
    setTimeout(() => {
      roleIdx.value = (roleIdx.value + 1) % roles.length
      roleVisible.value = true
    }, 380)
  }, 3200)
})

onBeforeUnmount(() => {
  document.documentElement.removeAttribute('data-layout')
  document.documentElement.removeAttribute('data-hero')
  clearInterval(roleTimer)
  if (raf) cancelAnimationFrame(raf)
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', resizeCanvas)
    window.removeEventListener('scroll', updateNavMode)
  }
})
</script>

<template>
  <div class="hp">

    <!-- ══════════════════════ HERO ══════════════════════ -->
    <section class="hp-hero">
      <canvas id="hp-canvas" class="hp-canvas" aria-hidden="true" />

      <!-- Decorative orbs -->
      <div class="hp-orb hp-orb-1" />
      <div class="hp-orb hp-orb-2" />
      <div class="hp-orb hp-orb-3" />

      <div class="hp-hero-inner">
        <!-- Left: Text -->
        <div class="hp-hero-text">
          <p class="hp-greeting">
            Hi&nbsp;there,&nbsp;I'm&nbsp;
            <span class="hp-role" :class="{ 'hp-role--out': !roleVisible }">
              {{ roles[roleIdx] }}
            </span>
          </p>

          <h1 class="hp-title">
            GXC の<br />
            <span class="hp-title-shine">技术笔记</span>
          </h1>

          <p class="hp-bio">
            记录技术成长路上的每一块拼图<br />
            从基础语法到工程实践，持续更新中
          </p>

          <div class="hp-actions">
            <a :href="withBase('/front-end/the-basics/js-basics/event-loop')" class="hp-btn hp-btn-brand">
              开始阅读&nbsp;<span class="hp-arrow">→</span>
            </a>
            <a
              href="https://github.com/xincheng-1999/vitepress-doc"
              target="_blank" rel="noopener noreferrer"
              class="hp-btn hp-btn-ghost"
            >
              GitHub
            </a>
          </div>

          <div class="hp-stats">
            <div class="hp-stat">
              <span class="hp-stat-n">6</span>
              <span class="hp-stat-l">技术分区</span>
            </div>
            <div class="hp-stat-sep" />
            <div class="hp-stat">
              <span class="hp-stat-n">120+</span>
              <span class="hp-stat-l">篇笔记</span>
            </div>
            <div class="hp-stat-sep" />
            <div class="hp-stat">
              <span class="hp-stat-n">✦</span>
              <span class="hp-stat-l">持续更新</span>
            </div>
          </div>
        </div>

        <!-- Right: Image -->
        <div class="hp-hero-img">
          <div class="hp-img-ring hp-img-ring-1" />
          <div class="hp-img-ring hp-img-ring-2" />
          <div class="hp-img-glow" />
          <img :src="withBase('/Ironman.png')" alt="GXC Mascot" class="hp-ironman" />
        </div>
      </div>

      <!-- Scroll indicator -->
      <div class="hp-scroll" aria-hidden="true">
        <span class="hp-scroll-label">向下探索</span>
        <div class="hp-scroll-dot" />
        <div class="hp-scroll-dot" />
        <div class="hp-scroll-dot" />
      </div>
    </section>

    <!-- ══════════════════════ CARDS ══════════════════════ -->
    <section class="hp-section hp-cards-wrap">
      <div class="hp-section-head">
        <div class="hp-section-badge">EXPLORE</div>
        <h2 class="hp-section-title">探索内容</h2>
        <p class="hp-section-sub">按分区浏览，找到你需要的知识</p>
      </div>

      <div class="hp-cards">
        <a
          v-for="c in navCards"
          :key="c.title"
          :href="withBase(c.link)"
          class="hp-card"
          :style="{ '--cc': c.color }"
        >
          <!-- Colored left border on hover -->
          <span class="hp-card-bar" />
          <!-- Top gradient wash on hover -->
          <div class="hp-card-wash" />

          <span class="hp-card-icon">{{ c.icon }}</span>

          <div class="hp-card-content">
            <div class="hp-card-header">
              <span class="hp-card-title">{{ c.title }}</span>
              <span class="hp-card-badge">{{ c.badge }} 篇</span>
            </div>
            <p class="hp-card-desc">{{ c.desc }}</p>
          </div>

          <span class="hp-card-arrow">→</span>
        </a>
      </div>
    </section>

    <!-- ══════════════════════ MARQUEE ══════════════════════ -->
    <div class="hp-marquee-wrap" aria-hidden="true">
      <div class="hp-marquee-fade hp-marquee-fade-l" />
      <div class="hp-marquee-track">
        <div class="hp-marquee-inner">
          <span v-for="(t, i) in [...techTags, ...techTags]" :key="i" class="hp-chip">
            {{ t }}
          </span>
        </div>
      </div>
      <div class="hp-marquee-fade hp-marquee-fade-r" />
    </div>

    <!-- ══════════════════════ CTA ══════════════════════ -->
    <section class="hp-cta">
      <!-- Grid pattern backdrop -->
      <div class="hp-cta-grid" />

      <div class="hp-cta-inner">
        <div class="hp-cta-glow" />
        <p class="hp-cta-tag">GET STARTED</p>
        <h2 class="hp-cta-title">开始你的技术旅程</h2>
        <p class="hp-cta-sub">从基础到进阶，每篇笔记都是一次成长记录</p>
        <div class="hp-cta-actions">
          <a :href="withBase('/front-end/the-basics/js-basics/event-loop')" class="hp-btn hp-btn-brand hp-btn-lg">
            立即开始&nbsp;<span class="hp-arrow">→</span>
          </a>
          <a
            href="https://roadmap.sh/frontend"
            target="_blank" rel="noopener noreferrer"
            class="hp-btn hp-btn-outline-light hp-btn-lg"
          >前端路线图</a>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
/* ─────────────────────────────────────────────────────────
   Base
───────────────────────────────────────────────────────── */
.hp {
  font-family: var(--vp-font-family-base);
  line-height: 1;
}

/* ─────────────────────────────────────────────────────────
   Hero
───────────────────────────────────────────────────────── */
.hp-hero {
  position: relative;
  min-height: calc(100vh - var(--vp-nav-height, 64px));
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: linear-gradient(140deg, #07071a 0%, #190433 45%, #07172a 100%);
  overflow: hidden;
  padding-left: max(28px, calc((100vw - 1200px) / 2));
  padding-right: max(28px, calc((100vw - 1200px) / 2));
}

/* canvas */
.hp-canvas {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

/* decorative blurred orbs */
.hp-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
  opacity: 0.45;
}
.hp-orb-1 {
  width: 500px; height: 500px;
  background: radial-gradient(circle, #f06292, transparent 70%);
  top: -120px; right: 80px;
  animation: orb-drift 8s ease-in-out infinite;
}
.hp-orb-2 {
  width: 380px; height: 380px;
  background: radial-gradient(circle, #8e24aa, transparent 70%);
  bottom: -60px; left: 60px;
  animation: orb-drift 11s ease-in-out infinite reverse;
}
.hp-orb-3 {
  width: 260px; height: 260px;
  background: radial-gradient(circle, #0288d1, transparent 70%);
  top: 40%; left: 40%;
  animation: orb-drift 14s ease-in-out infinite;
}
@keyframes orb-drift {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33%       { transform: translate(30px, -20px) scale(1.05); }
  66%       { transform: translate(-20px, 25px) scale(0.96); }
}

/* inner layout */
.hp-hero-inner {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 64px;
  padding: 64px 0 80px;
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}
.hp-hero-text { flex: 1; min-width: 0; }

/* greeting + role */
.hp-greeting {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 17px;
  color: rgba(255, 255, 255, 0.45);
  font-weight: 400;
  margin: 0 0 18px;
}
.hp-role {
  color: var(--vp-c-brand-1, #f48fb1);
  font-weight: 700;
  transition: opacity 0.38s ease, transform 0.38s ease;
}
.hp-role--out {
  opacity: 0;
  transform: translateY(-10px);
}

/* main title */
.hp-title {
  font-size: clamp(46px, 6vw, 80px);
  font-weight: 900;
  color: #fff;
  line-height: 1.1;
  letter-spacing: -0.03em;
  margin: 0 0 24px;
}
.hp-title-shine {
  background: linear-gradient(
    135deg,
    var(--vp-c-brand-1, #f48fb1) 0%,
    #ffca28 50%,
    var(--vp-c-brand-1, #f48fb1) 100%
  );
  background-size: 200% auto;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: shine 5s linear infinite;
}
@keyframes shine {
  to { background-position: 200% center; }
}

/* bio */
.hp-bio {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.58);
  line-height: 1.9;
  margin: 0 0 38px;
}

/* action buttons */
.hp-actions {
  display: flex;
  gap: 13px;
  flex-wrap: wrap;
  margin-bottom: 46px;
}
.hp-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 12px 28px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease;
  white-space: nowrap;
}
.hp-btn:hover { transform: translateY(-3px); }
.hp-btn-brand {
  background: linear-gradient(135deg, var(--vp-c-brand-1, #ec407a), var(--vp-c-brand-3, #f48fb1));
  color: #fff;
  box-shadow: 0 4px 22px rgba(236, 64, 122, 0.45);
}
.hp-btn-brand:hover { box-shadow: 0 8px 32px rgba(236, 64, 122, 0.7); }
.hp-btn-ghost {
  background: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.78);
  border: 1.5px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(8px);
}
.hp-btn-ghost:hover {
  background: rgba(255, 255, 255, 0.13);
  border-color: rgba(255, 255, 255, 0.38);
}
.hp-btn-outline-light {
  background: var(--vp-c-bg-soft);
  color: var(--vp-c-text-2);
  border: 1.5px solid var(--vp-c-divider);
}
.hp-btn-outline-light:hover {
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
}
.hp-btn-lg { padding: 14px 38px; font-size: 16px; }
.hp-arrow {
  display: inline-block;
  transition: transform 0.22s ease;
}
.hp-btn:hover .hp-arrow { transform: translateX(5px); }

/* stats row */
.hp-stats {
  display: flex;
  align-items: center;
  gap: 22px;
}
.hp-stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hp-stat-n {
  font-size: 24px;
  font-weight: 800;
  color: #fff;
  line-height: 1;
}
.hp-stat-l {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.38);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}
.hp-stat-sep {
  width: 1px;
  height: 38px;
  background: rgba(255, 255, 255, 0.12);
}

/* Hero image side */
.hp-hero-img {
  position: relative;
  flex-shrink: 0;
  width: 320px;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.hp-img-ring {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.hp-img-ring-1 {
  inset: -16px;
  animation: spin-slow 20s linear infinite;
}
.hp-img-ring-2 {
  inset: -36px;
  border-style: dashed;
  animation: spin-slow 30s linear infinite reverse;
}
@keyframes spin-slow {
  to { transform: rotate(360deg); }
}
.hp-img-glow {
  position: absolute;
  inset: -20px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(236, 64, 122, 0.42) 0%, transparent 68%);
  animation: glow-breathe 3.5s ease-in-out infinite;
}
@keyframes glow-breathe {
  0%, 100% { opacity: 0.65; transform: scale(1); }
  50%       { opacity: 1;    transform: scale(1.07); }
}
.hp-ironman {
  position: relative;
  z-index: 1;
  width: 260px;
  height: 260px;
  object-fit: contain;
  filter: drop-shadow(0 0 32px rgba(236, 64, 122, 0.6));
  animation: float 4.5s ease-in-out infinite;
  transition: transform 0.3s ease, animation-play-state 0.3s;
}
.hp-ironman:hover {
  transform: scale(1.08);
  animation-play-state: paused;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50%       { transform: translateY(-18px); }
}

/* Scroll hint */
.hp-scroll {
  position: absolute;
  bottom: 28px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}
.hp-scroll-label {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.28);
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.hp-scroll-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  animation: dot-fade 1.6s ease-in-out infinite;
}
.hp-scroll-dot:nth-child(3) { animation-delay: 0.25s; }
.hp-scroll-dot:nth-child(4) { animation-delay: 0.5s; }
@keyframes dot-fade {
  0%, 100% { opacity: 0.2; transform: scale(0.8); }
  50%       { opacity: 0.9; transform: scale(1.2); }
}

/* ─────────────────────────────────────────────────────────
   Shared Section Styles
───────────────────────────────────────────────────────── */
.hp-section {
  padding: 80px max(28px, calc((100vw - 1200px) / 2));
  background: var(--vp-c-bg);
}
.hp-section-head {
  text-align: center;
  margin-bottom: 54px;
}
.hp-section-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--vp-c-brand-1);
  border: 1px solid var(--vp-c-brand-1);
  padding: 3px 10px;
  border-radius: 999px;
  margin-bottom: 14px;
  opacity: 0.8;
}
.hp-section-title {
  font-size: 34px;
  font-weight: 800;
  color: var(--vp-c-text-1);
  margin: 0 0 12px;
  border: none !important;
  padding: 0 !important;
}
.hp-section-sub {
  font-size: 15px;
  color: var(--vp-c-text-3);
  margin: 0;
  line-height: 1.6;
}

/* ─────────────────────────────────────────────────────────
   Nav Cards
───────────────────────────────────────────────────────── */
.hp-cards-wrap { padding-top: 80px; padding-bottom: 80px; }
.hp-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 18px;
  max-width: 1200px;
  margin: 0 auto;
}
.hp-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 15px;
  padding: 24px 20px 24px 24px;
  border-radius: 16px;
  border: 1.5px solid var(--vp-c-divider);
  background: var(--vp-c-bg-elv);
  text-decoration: none;
  overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
}
.hp-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.12);
  border-color: var(--cc, var(--vp-c-brand-1));
}

/* colored bottom bar reveal */
.hp-card-bar {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 3px;
  background: var(--cc, var(--vp-c-brand-1));
  border-radius: 0 0 14px 14px;
  transform: scaleX(0);
  transform-origin: left;
  transition: transform 0.3s ease;
}
.hp-card:hover .hp-card-bar { transform: scaleX(1); }

/* gradient wash on hover */
.hp-card-wash {
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 0% 0%, var(--cc, var(--vp-c-brand-1)), transparent 60%);
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}
.hp-card:hover .hp-card-wash { opacity: 0.07; }

.hp-card-icon {
  font-size: 30px;
  line-height: 1;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.hp-card-content {
  flex: 1;
  min-width: 0;
  position: relative;
  z-index: 1;
}
.hp-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 9px;
}
.hp-card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--vp-c-text-1);
}
.hp-card-badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--cc, var(--vp-c-brand-1));
  border: 1px solid var(--cc, var(--vp-c-brand-1));
  padding: 2px 8px;
  border-radius: 999px;
  white-space: nowrap;
  opacity: 0.82;
}
.hp-card-desc {
  font-size: 13px;
  color: var(--vp-c-text-3);
  line-height: 1.7;
  margin: 0;
}
.hp-card-arrow {
  font-size: 18px;
  color: var(--vp-c-text-3);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  margin-top: 3px;
  transition: transform 0.22s ease, color 0.22s ease;
}
.hp-card:hover .hp-card-arrow {
  transform: translateX(6px);
  color: var(--cc, var(--vp-c-brand-1));
}

/* ─────────────────────────────────────────────────────────
   Marquee
───────────────────────────────────────────────────────── */
.hp-marquee-wrap {
  position: relative;
  padding: 30px 0;
  background: var(--vp-c-bg-soft);
  border-top: 1px solid var(--vp-c-divider);
  border-bottom: 1px solid var(--vp-c-divider);
  overflow: hidden;
}
.hp-marquee-fade {
  position: absolute;
  top: 0; bottom: 0; left: 0;
  width: 100px;
  background: linear-gradient(to right, var(--vp-c-bg-soft), transparent);
  z-index: 1;
  pointer-events: none;
}
.hp-marquee-fade-r {
  left: auto; right: 0;
  background: linear-gradient(to left, var(--vp-c-bg-soft), transparent);
}
.hp-marquee-track { overflow: hidden; }
.hp-marquee-inner {
  display: flex;
  gap: 10px;
  width: max-content;
  animation: scroll-x 32s linear infinite;
}
.hp-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 16px;
  border-radius: 999px;
  background: var(--vp-c-bg-elv);
  border: 1px solid var(--vp-c-divider);
  font-size: 13px;
  color: var(--vp-c-text-2);
  white-space: nowrap;
  transition: border-color 0.2s, color 0.2s;
  cursor: default;
}
.hp-chip:hover {
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
}
@keyframes scroll-x {
  from { transform: translateX(0); }
  to   { transform: translateX(-50%); }
}

/* ─────────────────────────────────────────────────────────
   CTA
───────────────────────────────────────────────────────── */
.hp-cta {
  position: relative;
  padding: 100px max(28px, calc((100vw - 1200px) / 2));
  background: linear-gradient(140deg, #07071a 0%, #190433 55%, #07172a 100%);
  text-align: center;
  overflow: hidden;
}

/* dot-grid backdrop */
.hp-cta-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 28px 28px;
  pointer-events: none;
}
.hp-cta-inner {
  position: relative;
  z-index: 1;
  max-width: 620px;
  margin: 0 auto;
}
.hp-cta-glow {
  position: absolute;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(236, 64, 122, 0.3), transparent 65%);
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
  animation: glow-breathe 4s ease-in-out infinite;
}
.hp-cta-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--vp-c-brand-1, #f48fb1);
  border: 1px solid rgba(240, 98, 146, 0.35);
  padding: 3px 12px;
  border-radius: 999px;
  margin: 0 0 18px;
  opacity: 0.85;
}
.hp-cta-title {
  font-size: 38px;
  font-weight: 900;
  color: #fff;
  margin: 0 0 14px;
  letter-spacing: -0.02em;
  line-height: 1.2;
}
.hp-cta-sub {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.55);
  line-height: 1.75;
  margin: 0 0 40px;
}
.hp-cta-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
}

/* ─────────────────────────────────────────────────────────
   Responsive
───────────────────────────────────────────────────────── */
@media (max-width: 860px) {
  .hp-hero {
    padding-left: 24px;
    padding-right: 24px;
  }
  .hp-hero-inner {
    flex-direction: column-reverse;
    align-items: center;
    text-align: center;
    gap: 28px;
    padding: 48px 0 90px;
  }
  .hp-hero-img { width: 210px; height: 210px; }
  .hp-ironman  { width: 180px; height: 180px; }
  .hp-greeting, .hp-stats, .hp-actions { justify-content: center; }
  .hp-title { font-size: 44px; }

  .hp-cards-wrap {
    padding-left: 20px;
    padding-right: 20px;
  }
  .hp-cards { grid-template-columns: 1fr; }

  .hp-cta {
    padding-left: 24px;
    padding-right: 24px;
  }
  .hp-cta-title { font-size: 28px; }
}
</style>
