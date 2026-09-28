import {AsyncLocalStorage} from 'node:async_hooks'

// Nested CLI entry points in one ordered operation keep the same filesystem
// lock. Independent async callers/processes must still acquire it themselves.
export function createOperationLock(acquire) {
  const context=new AsyncLocalStorage()
  return async function locked(action) {
    if(typeof action!=='function')throw new Error('An operation callback is required')
    const parent=context.getStore()
    if(parent?.active) {
      const pending=Promise.resolve().then(action);parent.pending.add(pending)
      try{return await pending}finally{parent.pending.delete(pending)}
    }
    const release=await acquire(),scope={active:true,pending:new Set()}
    try{return await context.run(scope,action)}
    finally{
      // Do not release process ownership before any already-started descendant
      // operation has run its own cleanup, even if its caller forgot to await it.
      while(scope.pending.size)await Promise.allSettled([...scope.pending])
      scope.active=false;await release()
    }
  }
}
