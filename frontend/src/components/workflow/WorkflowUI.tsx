import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Check, FileSearch, X } from 'lucide-react'

export function WorkflowPageHeader({ eyebrow, title, copy, actions }: { eyebrow:string; title:string; copy:string; actions?:ReactNode }) {
  return <header className="workflow-page-header"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p>{copy}</p></div>{actions&&<div className="workflow-page-actions">{actions}</div>}</header>
}

export function WorkflowStatus({ value }: { value:string }) {
  const className=value.toLowerCase().replaceAll('_','-').replaceAll(' ','-')
  return <span className={`workflow-status workflow-status--${className}`}><i/>{value.replaceAll('_',' ')}</span>
}

export function WorkflowNotice({ message, error=false }: { message:string; error?:boolean }) {
  if(!message)return null
  return <p className={`workflow-notice ${error?'workflow-notice--error':''}`} role={error?'alert':'status'}>{!error&&<Check/>}{message}</p>
}

export function WorkflowEmpty({ title, copy }: { title:string; copy:string }) {
  return <div className="workflow-empty"><FileSearch/><strong>{title}</strong><span>{copy}</span></div>
}

export function WorkflowDrawer({ open, close, title, eyebrow, copy, children, wide=false }: { open:boolean; close():void; title:string; eyebrow:string; copy:string; children:ReactNode; wide?:boolean }) {
  const ref=useRef<HTMLElement>(null);const titleId=useId();const closeRef=useRef(close);closeRef.current=close
  useEffect(()=>{if(!open)return;const previous=document.activeElement instanceof HTMLElement?document.activeElement:null;const oldOverflow=document.body.style.overflow;document.body.style.overflow='hidden';const controls=()=>Array.from(ref.current?.querySelectorAll<HTMLElement>('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled])')||[]);requestAnimationFrame(()=>controls()[0]?.focus());const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){event.preventDefault();closeRef.current();return}if(event.key!=='Tab')return;const items=controls();if(!items.length)return;if(event.shiftKey&&document.activeElement===items[0]){event.preventDefault();items.at(-1)?.focus()}else if(!event.shiftKey&&document.activeElement===items.at(-1)){event.preventDefault();items[0].focus()}};document.addEventListener('keydown',key);return()=>{document.removeEventListener('keydown',key);document.body.style.overflow=oldOverflow;previous?.focus()}},[open])
  if(!open)return null
  return <div className="workflow-drawer"><button className="workflow-drawer__scrim" onClick={close} aria-label={`Close ${title}`}/><aside ref={ref} className={wide?'wide':''} role="dialog" aria-modal="true" aria-labelledby={titleId}><header><div><p className="eyebrow">{eyebrow}</p><h2 id={titleId}>{title}</h2><p>{copy}</p></div><button type="button" className="icon-button" onClick={close} aria-label={`Close ${title}`}><X/></button></header>{children}</aside></div>
}

export function WorkflowFormActions({ cancel, submitLabel, busy=false }: { cancel():void; submitLabel:string; busy?:boolean }) {
  return <footer className="workflow-form-actions"><button type="button" className="secondary" onClick={cancel}>Cancel</button><button className="primary" disabled={busy}>{busy?'Saving…':submitLabel}</button></footer>
}

export const formatMoney=(value:number)=>new Intl.NumberFormat('en-US',{style:'currency',currency:'USD',maximumFractionDigits:0}).format(value)
export const formatDate=(value:string)=>new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',year:'numeric'}).format(new Date(value))
export const formatDateTime=(value:string)=>new Intl.DateTimeFormat('en-US',{month:'short',day:'numeric',hour:'numeric',minute:'2-digit'}).format(new Date(value))
