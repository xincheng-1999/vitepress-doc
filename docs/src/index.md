---
layout: home

hero:
  name: GXC's Notes
  text: Front-end & IT technology.
  tagline: Lorem ipsum...
  image:
    src: /Ironman.png
    alt: VitePress
  actions:
    - theme: brand
      text: Get Started
      link: /front-end/interview-questions/handwritten-code
    - theme: alt
      text: View on GitHub
      link: https://github.com/xincheng-1999/vitepress-doc
    - theme: brand
      text: 前端学习路线
      link: https://roadmap.sh/frontend

features:
  - icon: 🛠️
    title: Simple and minimal, always
    details: Lorem ipsum...
  - icon: 🔨
    title: Another cool feature
    details: Lorem ipsum...
  - icon: 🍀
    title: Another cool feature
    details: Lorem ipsum...
---

<script setup>
  import { gsap } from "gsap";
  import { onMounted, onBeforeUnmount } from 'vue'
  let interval = -1
  onMounted(() => {
    console.log('mounted')
    const img = document.querySelector('.image-src')
    img.addEventListener('mouseenter', () => {
      gsap.to(img, {
        scale:  2,
        duration: 0.2,
      })
    })
    img.addEventListener('mouseleave', () => {
      gsap.to(img, {
        scale:  1,
        duration: 0.2,
      })
    })
      
    // let direction = 1
    // interval = setInterval(() => {
    //   gsap.to(".image-src", {
    //     // this is the vars object
    //     // it contains properties to animate
    //     x: 20 * direction,
    //     // scale:  2,
    //     // rotation: 360,
    //     // and special properties
    //     duration: 0.2,
    //   })
    //   direction *= -1
    // }, 200)
    
  })
  onBeforeUnmount(() => {
    clearInterval(interval)
  })
  
</script>

<style>

  :root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #bfc, #41d1ff);
  --vp-home-hero-image-background-image: linear-gradient(-45deg, #f06292 30%, #ffb0cc)
}
</style>
