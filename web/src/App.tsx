import React, { useState, useEffect } from 'react';
import { Navbar } from './components/Navbar.tsx';
import { ConversationalSearch } from './components/ConversationalSearch.tsx';
import { SmartVault } from './components/SmartVault.tsx';
import { CloudSyncView } from './components/CloudSyncView.tsx';
import { DocumentInspectorModal } from './components/DocumentInspectorModal.tsx';
import { UploadModal } from './components/UploadModal.tsx';
import { IosInstallPrompt } from './components/IosInstallPrompt.tsx';
import { Footer } from './components/Footer.tsx';
import { DocumentEntity } from './types.ts';
import { 
  ensureAuthUser, 
  subscribeToDocuments, 
  auth, 
  loginWithGoogle,
  logoutUser
} from './firebase.ts';
import { Sparkles, Folder, Cloud } from 'lucide-react';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'search' | 'vault' | 'sync'>('search');
  const [documents, setDocuments] = useState<DocumentEntity[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<DocumentEntity | null>(null);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [userId, setUserId] = useState<string>('');
  const [isAnonymous, setIsAnonymous] = useState(true);
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [userPhotoUrl, setUserPhotoUrl] = useState<string | null>(null);

  // 1. Maintain Firebase Auth session
  useEffect(() => {
    const unsubAuth = auth.onAuthStateChanged((user) => {
      if (user) {
        setUserId(user.uid);
        setIsAnonymous(user.isAnonymous);
        setUserEmail(user.email);
        setUserPhotoUrl(user.photoURL);
      } else {
        ensureAuthUser().then((anon) => {
          if (anon) {
            setUserId(anon.uid);
            setIsAnonymous(anon.isAnonymous);
            setUserEmail(anon.email);
            setUserPhotoUrl(anon.photoURL);
          }
        });
      }
    });

    return () => unsubAuth();
  }, []);

  // 2. Subscribe to real-time updates from Firestore whenever userId changes
  useEffect(() => {
    if (!userId) return;
    const unsubscribe = subscribeToDocuments(userId, (updatedDocs) => {
      setDocuments(updatedDocs);
    });

    return () => unsubscribe();
  }, [userId]);

  const handleDocumentAdded = (newDoc: DocumentEntity) => {
    setDocuments((prev) => [newDoc, ...prev]);
    setSelectedDocument(newDoc);
  };

  return (
    <div className="min-h-screen bg-[#0B0F19] text-gray-100 flex flex-col pb-20 md:pb-10">
      {/* Top Navigation */}
      <Navbar
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onOpenUpload={() => setIsUploadOpen(true)}
        userId={userId}
        isAnonymous={isAnonymous}
        userEmail={userEmail}
        userPhotoUrl={userPhotoUrl}
        onLoginGoogle={async () => {
          const user = await loginWithGoogle();
          if (user) {
            setUserId(user.uid);
            setIsAnonymous(user.isAnonymous);
            setUserEmail(user.email);
            setUserPhotoUrl(user.photoURL);
          }
        }}
        onLogout={async () => {
          await logoutUser();
        }}
      />

      {/* Main Content Area */}
      <main className="flex-1 max-w-6xl w-full mx-auto px-4 sm:px-6 pt-6">
        {activeTab === 'search' && (
          <ConversationalSearch
            documents={documents}
            onSelectDocument={(doc) => setSelectedDocument(doc)}
          />
        )}

        {activeTab === 'vault' && (
          <SmartVault
            documents={documents}
            onSelectDocument={(doc) => setSelectedDocument(doc)}
          />
        )}

        {activeTab === 'sync' && (
          <CloudSyncView
            userId={userId}
            isAnonymous={isAnonymous}
            userEmail={userEmail}
            totalDocs={documents.length}
          />
        )}
      </main>

      {/* Official Compliant Footer */}
      <Footer />

      {/* Mobile Bottom Tab Bar */}
      <nav className="md:hidden fixed bottom-0 inset-x-0 bg-[#111827]/95 backdrop-blur-md border-t border-gray-800 z-40 px-6 py-2 flex items-center justify-around">
        <button
          onClick={() => setActiveTab('search')}
          className={`flex flex-col items-center space-y-1 ${
            activeTab === 'search' ? 'text-blue-400 font-bold' : 'text-gray-400'
          }`}
        >
          <Sparkles className="w-5 h-5" />
          <span className="text-[10px]">Ask AI</span>
        </button>
        <button
          onClick={() => setActiveTab('vault')}
          className={`flex flex-col items-center space-y-1 ${
            activeTab === 'vault' ? 'text-blue-400 font-bold' : 'text-gray-400'
          }`}
        >
          <Folder className="w-5 h-5" />
          <span className="text-[10px]">Vault</span>
        </button>
        <button
          onClick={() => setActiveTab('sync')}
          className={`flex flex-col items-center space-y-1 ${
            activeTab === 'sync' ? 'text-blue-400 font-bold' : 'text-gray-400'
          }`}
        >
          <Cloud className="w-5 h-5" />
          <span className="text-[10px]">Sync</span>
        </button>
      </nav>

      {/* Modals & Prompts */}
      <DocumentInspectorModal
        document={selectedDocument}
        onClose={() => setSelectedDocument(null)}
        onUpdate={(updated) => {
          setSelectedDocument(updated);
          setDocuments((prev) => prev.map((d) => (d.id === updated.id ? updated : d)));
        }}
      />

      <UploadModal
        userId={userId}
        isOpen={isUploadOpen}
        onClose={() => setIsUploadOpen(false)}
        onDocumentAdded={handleDocumentAdded}
      />

      {/* Native iOS Safari Home Screen Installation Guide */}
      <IosInstallPrompt />
    </div>
  );
};

export default App;

