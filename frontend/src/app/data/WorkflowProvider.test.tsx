import { fireEvent, render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { DemoDataProvider } from './DemoDataProvider'
import { WorkflowProvider, useWorkflow } from './WorkflowProvider'

function Providers({ children }: { children: React.ReactNode }) {
  return <AuthProvider><DemoDataProvider><WorkflowProvider>{children}</WorkflowProvider></DemoDataProvider></AuthProvider>
}

function FinanceProbe() {
  const { invoices, recordPayment, budgets, updateExpense } = useWorkflow()
  const invoice = invoices.find(item => item.id === 'INV-7001')!
  const budget = budgets.find(item => item.id === 'BUD-101')!
  return <>
    <output data-testid="invoice">{invoice.status}:{invoice.paidAmount}</output>
    <button onClick={() => recordPayment({ invoiceId:invoice.id, amount:invoice.totalAmount, method:'CARD', reference:'TEST-PAYMENT' })}>Pay invoice</button>
    <output data-testid="budget">{budget.spent}</output>
    <button onClick={() => updateExpense('EXP-501', 'APPROVED')}>Approve again</button>
  </>
}

function AdmissionProbe() {
  const { admissions, beds, updateAdmission } = useWorkflow()
  const admission = admissions.find(item => item.id === 'ADM-702')!
  const bed = beds.find(item => item.id === 'BED-E014')!
  return <>
    <output data-testid="admission">{admission.status}</output>
    <output data-testid="bed">{bed.status}:{bed.patientId || 'empty'}</output>
    <button onClick={() => updateAdmission(admission.id, 'ADMITTED', admission.departmentId, bed.id)}>Admit</button>
    <button onClick={() => updateAdmission(admission.id, 'DISCHARGED', admission.departmentId, bed.id)}>Discharge</button>
  </>
}

function MessagingProbe() {
  const { workflowMessages, sendWorkflowMessage } = useWorkflow()
  const thread = workflowMessages.filter(item => item.conversationId === 'CONV-101')
  return <>
    <output data-testid="message-count">{thread.length}</output>
    <output data-testid="latest-message">{thread.at(-1)?.body}</output>
    <button onClick={() => sendWorkflowMessage('CONV-101', 'Shared follow-up from the test')}>Send shared message</button>
  </>
}

describe('connected demo workflows', () => {
  beforeEach(() => localStorage.clear())

  it('settles an invoice and does not double count an approved expense', () => {
    render(<Providers><FinanceProbe /></Providers>)
    const initialSpend = screen.getByTestId('budget').textContent
    fireEvent.click(screen.getByRole('button', { name:'Approve again' }))
    expect(screen.getByTestId('budget')).toHaveTextContent(initialSpend!)
    fireEvent.click(screen.getByRole('button', { name:'Pay invoice' }))
    expect(screen.getByTestId('invoice')).toHaveTextContent('PAID:184')
  })

  it('keeps admission and bed state synchronized through discharge', () => {
    render(<Providers><AdmissionProbe /></Providers>)
    fireEvent.click(screen.getByRole('button', { name:'Admit' }))
    expect(screen.getByTestId('admission')).toHaveTextContent('ADMITTED')
    expect(screen.getByTestId('bed')).toHaveTextContent('OCCUPIED:PT-0884')
    fireEvent.click(screen.getByRole('button', { name:'Discharge' }))
    expect(screen.getByTestId('admission')).toHaveTextContent('DISCHARGED')
    expect(screen.getByTestId('bed')).toHaveTextContent('CLEANING:empty')
  })

  it('persists a message in the conversation shared by patient and doctor views', () => {
    render(<Providers><MessagingProbe /></Providers>)
    expect(screen.getByTestId('message-count')).toHaveTextContent('1')
    fireEvent.click(screen.getByRole('button', { name:'Send shared message' }))
    expect(screen.getByTestId('message-count')).toHaveTextContent('2')
    expect(screen.getByTestId('latest-message')).toHaveTextContent('Shared follow-up from the test')
  })
})
