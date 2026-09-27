import { notFound } from "next/navigation";

import { PublicStatusBadge } from "@/components/public/PublicPageParts";
import { ShareButton } from "@/components/public/ShareButton";
import { Card } from "@/components/ui/Card";
import { fetchPublicPost } from "@/lib/public/api";
import { formatDateEs } from "@/lib/public/format";
import { buildPublicMetadata } from "@/lib/public/metadata";
import { canonicalPublicUrl, publicPostPath, resolvePublicImageUrl } from "@/lib/public/urls";

type PageProps = {
  params: Promise<{ id: string }>;
};

export const revalidate = 60;

function splitBody(body?: string | null): { title: string; content: string } {
  const trimmed = body?.trim() ?? "";
  if (!trimmed) {
    return { title: "", content: "" };
  }
  const parts = trimmed.split("\n\n");
  if (parts.length >= 2 && parts[0].split("\n").length === 1) {
    return { title: parts[0].trim(), content: parts.slice(1).join("\n\n").trim() };
  }
  return { title: "", content: trimmed };
}

export async function generateMetadata({ params }: PageProps) {
  const { id } = await params;
  const post = await fetchPublicPost(id);

  if (!post) {
    return buildPublicMetadata({
      title: "Publicación no disponible | LeoVer",
      description: "Esta publicación no está disponible.",
      path: publicPostPath(id),
      index: false,
    });
  }

  const { title, content } = splitBody(post.body);
  const headline = title || content.slice(0, 80) || "Publicación en LeoVer";

  return buildPublicMetadata({
    title: `${headline} | LeoVer`,
    description: content.slice(0, 160) || "Contenido compartido en LeoVer.",
    path: publicPostPath(id),
  });
}

export default async function PublicPostPage({ params }: PageProps) {
  const { id } = await params;
  const post = await fetchPublicPost(id);

  if (!post) {
    notFound();
  }

  const { title, content } = splitBody(post.body);
  const mediaUrl = resolvePublicImageUrl(
    post.media_bucket && post.media_path
      ? `storage:${post.media_bucket}/${post.media_path}`
      : null,
  );
  const shareUrl = canonicalPublicUrl(publicPostPath(id));

  return (
    <main className="mx-auto flex w-full max-w-2xl flex-col gap-6 px-4 py-10">
      <Card className="space-y-4 p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-sm text-muted-foreground">{post.author_name ?? "LeoVer"}</p>
            <h1 className="text-2xl font-semibold">{title || "Publicación"}</h1>
            {post.created_at ? (
              <p className="text-sm text-muted-foreground">{formatDateEs(post.created_at)}</p>
            ) : null}
          </div>
          <PublicStatusBadge label="Pública" tone="active" />
        </div>
        {content ? <p className="whitespace-pre-wrap text-base leading-relaxed">{content}</p> : null}
        {mediaUrl ? (
          post.media_mime?.startsWith("video/") ? (
            <video controls className="w-full rounded-xl" src={mediaUrl} />
          ) : (
            // eslint-disable-next-line @next/next/no-img-element
            <img alt="" className="w-full rounded-xl object-cover" src={mediaUrl} />
          )
        ) : null}
        <ShareButton
          url={shareUrl}
          title={title || "Publicación en LeoVer"}
          text="Mirá esta publicación en LeoVer."
        />
      </Card>
    </main>
  );
}
