<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const themes = [
  { id: 'sakura', name: '樱花粉', color: '#ec407a', emoji: '🌸' },
  { id: 'ocean',  name: '深海蓝', color: '#0288d1', emoji: '🌊' },
  { id: 'aurora', name: '极光绿', color: '#00897b', emoji: '🌿' },
  { id: 'nebula', name: '星云紫', color: '#8e24aa', emoji: '💫' },
  { id: 'lava',   name: '熔岩橙', color: '#f4511e', emoji: '🔥' },
]

const current = ref('sakura')
const isOpen = ref(false)
const panelRef = ref(null)

function applyTheme(id) {
  current.value = id
  const html = document.documentElement
  html.classList.add('theme-transitioning')
  html.setAttribute('data-theme', id)
  localStorage.setItem('vp-color-theme', id)
  setTimeout(() => html.classList.remove('theme-transitioning'), 500)
  isOpen.value = false
}

function handleClickOutside(e) {
  if (panelRef.value && !panelRef.value.contains(e.target)) {
    isOpen.value = false
  }
}

onMounted(() => {
  const saved = localStorage.getItem('vp-color-theme') || 'sakura'
  current.value = saved
  document.documentElement.setAttribute('data-theme', saved)
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})
</script>

<template>
  <div class="vp-theme-switcher" ref="panelRef">
    <!-- 主题列表面板 -->
    <Transition name="panel">
      <div v-if="isOpen" class="vp-theme-panel">
        <div class="vp-theme-panel-title">🎨 主题色</div>
        <button
          v-for="t in themes"
          :key="t.id"
          class="vp-theme-option"
          :class="{ active: current === t.id }"
          @click.stop="applyTheme(t.id)"
        >
          <span class="vp-theme-swatch" :style="{ background: t.color }"></span>
          <span class="vp-theme-label">{{ t.emoji }} {{ t.name }}</span>
          <span v-if="current === t.id" class="vp-theme-check">✓</span>
        </button>
      </div>
    </Transition>

    <!-- 触发按钮 -->
    <button
      class="vp-theme-trigger"
      :class="{ open: isOpen }"
      @click.stop="isOpen = !isOpen"
      :title="isOpen ? '关闭主题面板' : '切换主题色'"
    >
      <span
        class="vp-trigger-dot"
        :style="{ background: themes.find(t => t.id === current)?.color }"
      ></span>
      <span class="vp-trigger-text">主题</span>
      <span class="vp-trigger-arrow">{{ isOpen ? '▾' : '▴' }}</span>
    </button>
  </div>
</template>

<style scoped>
.vp-theme-switcher {
  position: fixed;
  bottom: 28px;
  left: 24px;
  z-index: 999;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

/* 触发按钮 */
.vp-theme-trigger {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 8px 14px 8px 10px;
  border-radius: 999px;
  border: 1.5px solid var(--vp-c-divider);
  background: var(--vp-c-bg-elv);
  color: var(--vp-c-text-2);
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
  backdrop-filter: blur(8px);
}
.vp-theme-trigger:hover {
  border-color: var(--vp-c-brand-1);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.18);
  transform: translateY(-1px);
}
.vp-theme-trigger.open {
  border-color: var(--vp-c-brand-1);
  color: var(--vp-c-brand-1);
}
.vp-trigger-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}
.vp-trigger-text {
  letter-spacing: 0.02em;
}
.vp-trigger-arrow {
  font-size: 10px;
  opacity: 0.7;
}

/* 面板 */
.vp-theme-panel {
  background: var(--vp-c-bg-elv);
  border: 1.5px solid var(--vp-c-divider);
  border-radius: 14px;
  padding: 10px 8px 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.16);
  min-width: 148px;
  backdrop-filter: blur(12px);
}
.vp-theme-panel-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--vp-c-text-3);
  text-align: center;
  padding: 2px 6px 8px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--vp-c-divider);
  margin-bottom: 6px;
}

/* 主题选项 */
.vp-theme-option {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--vp-c-text-2);
  cursor: pointer;
  font-size: 13px;
  transition: background 0.15s, color 0.15s;
  position: relative;
}
.vp-theme-option:hover {
  background: var(--vp-c-bg-mute);
  color: var(--vp-c-text-1);
}
.vp-theme-option.active {
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1);
  font-weight: 600;
}
.vp-theme-swatch {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}
.vp-theme-label {
  flex: 1;
  text-align: left;
}
.vp-theme-check {
  font-size: 12px;
  color: var(--vp-c-brand-1);
  font-weight: 700;
}

/* 面板动画 */
.panel-enter-active,
.panel-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.96);
}
</style>
