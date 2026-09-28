import test from 'node:test'
import assert from 'node:assert/strict'
import {randomUUID,createHash} from 'node:crypto'
import {validateAttachmentState,bindUpload} from './synthetic-message-attachments.mjs'
import {syntheticPdf} from './synthetic/file-seed.mjs'
const state=()=>({kind:'sahha-synthetic-message-attachments-v1',generation:randomUUID(),organisationId:randomUUID(),
  conversationRequestId:randomUUID(),conversationId:randomUUID()})
const command=s=>({uploadRequestId:randomUUID(),messageRequestId:randomUUID(),conversationId:s.conversationId,
  originalFilename:'synthetic-message-only.pdf',contentType:'application/pdf',declaredSize:syntheticPdf().length,
  checksumSha256:createHash('sha256').update(syntheticPdf()).digest('hex')})
test('retained scope rejects foreign generations and private credential fields',()=>{
  const s=state();assert.equal(validateAttachmentState(s,s.generation),s)
  assert.throws(()=>validateAttachmentState(s,randomUUID()))
  assert.throws(()=>validateAttachmentState({...s,downloadToken:'secret'},s.generation))
})
test('journal never claims completion without positive browser evidence',()=>{
  const s=state();assert.throws(()=>validateAttachmentState({...s,verifiedAt:new Date().toISOString()},s.generation))
  assert.throws(()=>validateAttachmentState({...s,fileId:randomUUID()},s.generation))
})
test('first browser command is retained and retry reuses immutable IDs',()=>{
  const s=state(),first=command(s);assert.deepEqual(bindUpload(first,s),first)
  const retry=command(s),bound=bindUpload(retry,s)
  assert.equal(bound.uploadRequestId,first.uploadRequestId);assert.equal(bound.messageRequestId,first.messageRequestId)
  assert.equal(validateAttachmentState(s,s.generation),s)
})
test('different bytes, filename, conversation or injected fields cannot be replayed',()=>{
  const s=state(),first=command(s)
  for(const changed of [{checksumSha256:'0'.repeat(64)},{declaredSize:1},{conversationId:randomUUID()},
    {originalFilename:'clinical.pdf'},{contentType:'image/svg+xml'},{fileId:randomUUID()}])
    assert.throws(()=>bindUpload({...first,...changed},s))
})
