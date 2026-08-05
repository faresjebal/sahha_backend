import type { Doctor, Patient } from './models/healthcare'

export type { Doctor, Patient } from './models/healthcare'

export type Review = { id: number; doctorId: number; author: string; rating: number; date: string; text: string; status: 'Published' | 'Pending' }

export const doctors: Doctor[] = [
  { id: 1, name: 'Dr. Mara Voss', initials: 'MV', specialty: 'Cardiology', location: 'North Clinic', rating: 4.9, experience: 14, next: 'Today · 16:30', accent: '#9cc6a7', languages: ['English', 'French'] },
  { id: 2, name: 'Dr. Elias Chen', initials: 'EC', specialty: 'Neurology', location: 'Central Medical', rating: 4.8, experience: 11, next: 'Tomorrow · 09:00', accent: '#c9b98b', languages: ['English', 'Mandarin'] },
  { id: 3, name: 'Dr. Sanaa Idris', initials: 'SI', specialty: 'Internal medicine', location: 'Harbor Practice', rating: 5.0, experience: 9, next: 'Today · 18:15', accent: '#8fb7bd', languages: ['English', 'Arabic'] },
  { id: 4, name: 'Dr. Luca Marin', initials: 'LM', specialty: 'Orthopedics', location: 'West Health', rating: 4.7, experience: 16, next: 'Wed · 11:45', accent: '#b6a1c2', languages: ['English', 'Italian'] },
]

export const reviews: Review[] = [
  { id: 1, doctorId: 1, author: 'Nora B.', rating: 5, date: '02 Jul 2026', text: 'Dr. Voss explained every result clearly and made the follow-up plan feel manageable.', status: 'Published' },
  { id: 2, doctorId: 1, author: 'Theo W.', rating: 5, date: '18 Jun 2026', text: 'Thoughtful, unhurried, and exceptionally thorough with medication questions.', status: 'Published' },
  { id: 3, doctorId: 2, author: 'Amelia R.', rating: 5, date: '10 Jul 2026', text: 'A calm consultation with a clear explanation of the imaging and next steps.', status: 'Pending' },
  { id: 4, doctorId: 3, author: 'Amir H.', rating: 5, date: '09 Jul 2026', text: 'Practical advice and genuine attention to the whole picture, not just one result.', status: 'Published' },
]

export const patients: Patient[] = [
  { id: 'PT-1048', name: 'Nora Bennett', initials: 'NB', age: 42, sex: 'Female', status: 'Monitor', condition: 'Hypertension', lastVisit: '11 Jul 2026', phone: '+1 202 555 0148', email: 'nora.b@example.com', blood: 'A+', allergies: ['Penicillin'], notes: 'Blood pressure remains above target in the evening. Review home readings after dosage adjustment.', vitals: [{ label: 'Blood pressure', value: '142/89', note: 'mmHg' }, { label: 'Heart rate', value: '76', note: 'bpm' }, { label: 'SpO₂', value: '98', note: '%' }, { label: 'Weight', value: '68.4', note: 'kg' }], medications: [{ name: 'Amlodipine', dose: '5 mg', schedule: 'Once daily' }, { name: 'Losartan', dose: '50 mg', schedule: 'Every morning' }], timeline: [{ date: '11 JUL', title: 'Follow-up consultation', detail: 'Adjusted amlodipine; requested 7-day home BP log.', type: 'Consultation' }, { date: '28 JUN', title: 'Metabolic panel', detail: 'Renal function and electrolytes within normal range.', type: 'Lab' }, { date: '03 MAY', title: 'Initial assessment', detail: 'Stage 2 hypertension confirmed by ambulatory monitoring.', type: 'Diagnosis' }] },
  { id: 'PT-0921', name: 'Amir Haddad', initials: 'AH', age: 58, sex: 'Male', status: 'Stable', condition: 'Type 2 diabetes', lastVisit: '09 Jul 2026', phone: '+1 202 555 0191', email: 'amir.h@example.com', blood: 'O+', allergies: ['None known'], notes: 'Excellent adherence. Continue current plan and repeat HbA1c in three months.', vitals: [{ label: 'HbA1c', value: '6.8', note: '%' }, { label: 'Blood pressure', value: '126/78', note: 'mmHg' }, { label: 'Heart rate', value: '71', note: 'bpm' }, { label: 'Weight', value: '81.2', note: 'kg' }], medications: [{ name: 'Metformin XR', dose: '1000 mg', schedule: 'With evening meal' }], timeline: [{ date: '09 JUL', title: 'Diabetes review', detail: 'Glycemic control improved; no hypoglycemic events.', type: 'Consultation' }, { date: '07 JUL', title: 'HbA1c result', detail: 'Reduced from 7.3% to 6.8%.', type: 'Lab' }] },
  { id: 'PT-1176', name: 'Maeve Kelly', initials: 'MK', age: 34, sex: 'Female', status: 'Critical', condition: 'Acute myocarditis', lastVisit: 'Today, 08:20', phone: '+1 202 555 0116', email: 'maeve.k@example.com', blood: 'B−', allergies: ['Ibuprofen'], notes: 'Escalated for inpatient observation following rising troponin and recurrent chest pain.', vitals: [{ label: 'Blood pressure', value: '102/64', note: 'mmHg' }, { label: 'Heart rate', value: '108', note: 'bpm' }, { label: 'SpO₂', value: '95', note: '%' }, { label: 'Temperature', value: '38.1', note: '°C' }], medications: [{ name: 'Colchicine', dose: '0.5 mg', schedule: 'Twice daily' }], timeline: [{ date: 'TODAY', title: 'Urgent review', detail: 'Transferred to monitored bed; cardiology team notified.', type: 'Alert' }, { date: '10 JUL', title: 'Cardiac MRI', detail: 'Findings consistent with active myocardial inflammation.', type: 'Imaging' }] },
  { id: 'PT-0884', name: 'Theo Walker', initials: 'TW', age: 66, sex: 'Male', status: 'Stable', condition: 'Atrial fibrillation', lastVisit: '02 Jul 2026', phone: '+1 202 555 0184', email: 'theo.w@example.com', blood: 'AB+', allergies: ['Latex'], notes: 'Rate controlled. Anticoagulation adherence confirmed.', vitals: [{ label: 'Blood pressure', value: '118/72', note: 'mmHg' }, { label: 'Heart rate', value: '68', note: 'bpm' }, { label: 'INR', value: '2.4', note: 'ratio' }, { label: 'Weight', value: '76.8', note: 'kg' }], medications: [{ name: 'Apixaban', dose: '5 mg', schedule: 'Twice daily' }, { name: 'Bisoprolol', dose: '2.5 mg', schedule: 'Once daily' }], timeline: [{ date: '02 JUL', title: 'Rhythm review', detail: 'Asymptomatic with adequate rate control.', type: 'Consultation' }] },
]

export const appointments = [
  { time: '09:00', patient: 'Amir Haddad', reason: 'Diabetes review', mode: 'In clinic', status: 'Complete' },
  { time: '10:30', patient: 'Nora Bennett', reason: 'BP follow-up', mode: 'Video', status: 'In 24 min' },
  { time: '12:00', patient: 'Maeve Kelly', reason: 'Cardiac MRI review', mode: 'Urgent', status: 'Priority' },
  { time: '14:15', patient: 'Theo Walker', reason: 'Medication review', mode: 'In clinic', status: 'Upcoming' },
  { time: '16:30', patient: 'Olivia Stone', reason: 'New consultation', mode: 'In clinic', status: 'Upcoming' },
]

export const conversations = [
  { id: 1, name: 'Dr. Elias Chen', initials: 'EC', role: 'Neurology', online: true, unread: 2, time: '10:14', messages: [{ from: 'them', text: 'I reviewed Maeve’s MRI. The inflammatory pattern looks focal rather than diffuse.', time: '10:08' }, { from: 'me', text: 'That aligns with the troponin trend. Would you still recommend neuro observation?', time: '10:10' }, { from: 'them', text: 'For 24 hours, yes. I’ll add my note to the shared record now.', time: '10:14' }] },
  { id: 2, name: 'Dr. Sanaa Idris', initials: 'SI', role: 'Internal medicine', online: true, unread: 0, time: '09:41', messages: [{ from: 'them', text: 'Amir’s latest metabolic panel is back. Everything is in range.', time: '09:38' }, { from: 'me', text: 'Perfect, thank you. I’ll keep his current dose.', time: '09:41' }] },
  { id: 3, name: 'Dr. Luca Marin', initials: 'LM', role: 'Orthopedics', online: false, unread: 0, time: 'Yesterday', messages: [{ from: 'me', text: 'Could you take a look at the shoulder imaging when you have a moment?', time: 'Yesterday' }] },
]
