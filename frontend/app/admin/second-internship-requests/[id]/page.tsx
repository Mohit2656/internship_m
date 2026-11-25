"use client";
import Link from "next/link";
import {useParams} from "next/navigation";
import {AppFrame} from "@/components/AppFrame";
import {ProtectedRoute} from "@/components/ProtectedRoute";
import {AdminDualInternshipPanel} from "@/components/DualInternshipPanel";
import {BackButton} from "@/components/BackButton";
export default function SecondInternshipRequestPage(){const params=useParams<{id:string}>();const id=Number(params.id);return <ProtectedRoute role="ADMIN"><AppFrame><main className="mx-auto max-w-4xl px-5 py-10 sm:px-8"><BackButton fallbackHref="/admin/second-internship-requests" fallbackLabel="Back to Second Internship Requests" /><h1 className="mt-5 text-3xl font-bold">Second Internship Request</h1>{Number.isSafeInteger(id)&&id>0?<AdminDualInternshipPanel requestId={id}/>:<p role="alert" className="mt-6 rounded-xl bg-red-50 p-4 text-red-700">Invalid second-internship request ID.</p>}</main></AppFrame></ProtectedRoute>}
