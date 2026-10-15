<script setup lang="ts">
import {computed,ref,onMounted,onUnmounted,watch,nextTick} from 'vue';
const props=defineProps<{items:Record<string,any>[]}>();
const viewport=ref<HTMLElement>(),top=ref(0),height=ref(600),sizes=ref<Record<string,number>>({});
const observers=new Map<Element,ResizeObserver>();
let viewportObserver:ResizeObserver|undefined;
const offsets=computed(()=>{const result=[0];for(const item of props.items)result.push(result.at(-1)!+(sizes.value[item.id]??280));return result;});
const range=computed(()=>{let start=0;while(start<props.items.length&&offsets.value[start+1]<Math.max(0,top.value-560))start++;let end=start;while(end<props.items.length&&offsets.value[end]<top.value+height.value+560)end++;return {start,end,before:offsets.value[start],after:offsets.value.at(-1)!-offsets.value[end]};});
const visible=computed(()=>props.items.slice(range.value.start,range.value.end));
function measure(element:Element|null,id:string){if(!element||observers.has(element))return;const observer=new ResizeObserver(entries=>{const size=entries[0].borderBoxSize?.[0]?.blockSize??element.getBoundingClientRect().height;if(size>0&&sizes.value[id]!==size)sizes.value={...sizes.value,[id]:size};});observer.observe(element);observers.set(element,observer);}
function scroll(){top.value=viewport.value?.scrollTop??0;height.value=viewport.value?.clientHeight??600;}
defineExpose({scrollStart:()=>{if(viewport.value)viewport.value.scrollTop=0;scroll();}});
watch(()=>props.items,async()=>{await nextTick();for(const [element,observer] of observers)if(!element.isConnected){observer.disconnect();observers.delete(element);}scroll();});
onMounted(()=>{viewportObserver=new ResizeObserver(scroll);if(viewport.value)viewportObserver.observe(viewport.value);scroll();});
onUnmounted(()=>{viewportObserver?.disconnect();observers.forEach(o=>o.disconnect());});
</script>
<template><div ref="viewport" class="virtual-events" @scroll="scroll"><div :style="{height:range.before+'px'}"></div><div v-for="item in visible" :key="item.id" :ref="el=>measure(el as Element,item.id)"><slot :event="item" /></div><div :style="{height:range.after+'px'}"></div></div></template>
<style scoped>.virtual-events{max-height:70vh;overflow:auto;overflow-anchor:none}</style>
